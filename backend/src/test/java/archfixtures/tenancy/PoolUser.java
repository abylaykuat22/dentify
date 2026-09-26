package archfixtures.tenancy;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

/** Фикстура ArchUnit: соединение через бин DataSource приложения, то есть через обёртку с клиникой, — правильно. */
public class PoolUser {

	public Connection connection(DataSource dataSource) throws SQLException {
		return dataSource.getConnection();
	}

}
