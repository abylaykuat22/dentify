package archfixtures.tenancy;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Фикстура ArchUnit: соединение из пула Hikari в обход обёртки, которая ставит клинику, — нарушение. Такое соединение
 * несёт клинику своего прошлого владельца.
 */
public class PoolUnwrapper {

	public Connection bypass(DataSource dataSource) throws SQLException {
		return dataSource.unwrap(HikariDataSource.class).getConnection();
	}

}
