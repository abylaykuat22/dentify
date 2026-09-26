package kz.dentify.platform.persistence;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.tenant.TenantContextHolder;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Пул соединений приложения, который ставит клинику текущего контекста в настройку {@code app.clinic_id} каждого
 * выданного соединения — её читают политики RLS через {@code current_clinic_id()} (PLT-02). Без контекста ставится
 * пустая строка: RLS не показывает ни одной строки таблиц арендатора.
 * <p>
 * Настройка ставится при каждой выдаче соединения, а не в начале транзакции, потому что к данным ходят и без
 * транзакции: методы запросов репозиториев Spring Data по умолчанию нетранзакционные, {@code JdbcTemplate} вне
 * {@code @Transactional}. Транзакция держит одно соединение от начала до конца, поэтому клиника в ней та, что была
 * при открытии; менять клинику внутри транзакции запрещает {@code TenantContext}.
 * <p>
 * Значение сессионное ({@code set_config(…, false)}) и перезаписывается при каждой выдаче — прежнее значение
 * соединения, вернувшегося в пул, следующий владелец не увидит, как бы тот ни завершил работу. Если пул когда-нибудь
 * станет выдавать соединения без автокоммита, установка фиксируется сразу: откат транзакции не вернёт прежнюю
 * клинику. Другие получатели соединений в обход этой обёртки: только Flyway (свой источник с ролью владельца схемы).
 */
final class ClinicConnectionDataSource extends DelegatingDataSource implements Closeable {

	static final String SET_CLINIC = "select set_config('app.clinic_id', ?, false)";

	ClinicConnectionDataSource(DataSource target) {
		super(target);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return bindClinic(obtainTargetDataSource().getConnection());
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return bindClinic(obtainTargetDataSource().getConnection(username, password));
	}

	/** Пул закрывается вместе с контекстом Spring: метод закрытия бина ищется у этой обёртки. */
	@Override
	public void close() throws IOException {
		if (obtainTargetDataSource() instanceof Closeable closeable) {
			closeable.close();
		}
	}

	static Connection bindClinic(Connection connection) throws SQLException {
		Tenant tenant = TenantContextHolder.get();
		String clinic = (tenant != null) ? tenant.clinicId().toString() : "";
		try {
			boolean autoCommit = connection.getAutoCommit();
			try (PreparedStatement statement = connection.prepareStatement(SET_CLINIC)) {
				statement.setString(1, clinic);
				try (ResultSet ignored = statement.executeQuery()) {
					// значение set_config не нужно
				}
			}
			if (!autoCommit) {
				connection.commit();
			}
			return connection;
		}
		catch (SQLException | RuntimeException ex) {
			try {
				connection.close();
			}
			catch (SQLException closeFailure) {
				ex.addSuppressed(closeFailure);
			}
			throw ex;
		}
	}

}
