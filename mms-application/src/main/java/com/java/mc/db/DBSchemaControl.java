package com.java.mc.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.java.mc.utils.Constants;
import com.java.mc.utils.SQLExtractor;

@Component
public class DBSchemaControl {
	private static final Logger logger = LoggerFactory.getLogger(DBSchemaControl.class);

	@Value("${vg.batch.sqlfile.prefix}")
	private String sqlFilePrefix;

	@Value("${vg.batch.sqlfile.suffix}")
	private String sqlFileSuffix;

	@Value("${vg.batch.current.db.version}")
	private int currentVersion;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${vg.batch.initialize}")
	private Boolean initialize;

	@Value("${vg.batch.schema}")
	private String schema;

	public void init() {

		if (!this.initialize) {
			return;
		}

		this.createSchema();
	}

	private void createSchema() {
		this.createSchema(this.getSchemaVersion());
	}

	private int getSchemaVersion() {
		if (!this.isInitialized()) {
			return -1;
		}
		try {
			String sql = "SELECT CODE FROM MMS_GLOBAL_CONFIGURATION WHERE NAME = '" + Constants.GLOBAL_VERSION + "' AND "
					+ Constants.ACTIVE + " = '" + Constants.Y + "'";
			Integer version = this.jdbcTemplate.queryForObject(sql, new RowMapper<Integer>() {

				@Override
				public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {
					return rs.getInt(1);
				}

			});
			return version;
		} catch (Exception e) {
			try {
				String sql = "SELECT CODE FROM GLOBAL_CONFIGURATION WHERE NAME = '" + Constants.GLOBAL_VERSION + "' AND "
						+ Constants.ACTIVE + " = '" + Constants.Y + "'";
				Integer version = this.jdbcTemplate.queryForObject(sql, new RowMapper<Integer>() {

					@Override
					public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getInt(1);
					}

				});
				return version;
			} catch (Exception e1) {
			}
		}
		return 0;
	}

	private boolean isInitialized() {
		try {
			this.jdbcTemplate.execute("SELECT 1 FROM users");
			return true;
		} catch (DataAccessException e) {
		}
		return false;
	}

	private void createSchema(int from) {
		if (from >= this.currentVersion) {
			return;
		}
		if (from < 0) {
			this.createSchema(this.schema);
			return;
		}
		this.createSchema(sqlFilePrefix + from + sqlFileSuffix);
		
		this.createSchema();
	}

	private void createSchema(String sqlFile) {
		Resource resource = new ClassPathResource(sqlFile);
		SQLExtractor sqlExtractor = SQLExtractor.getInstance();
		sqlExtractor.setKeepDelimiter(false);
		sqlExtractor.setEncoding("utf-8");
		try {
			List<String> sqlList = sqlExtractor.extract(resource.getInputStream());
			if (sqlList != null && sqlList.size() > 0) {
				for (String sql : sqlList) {
					logger.info(sql);
					this.jdbcTemplate.execute(sql);
				}
			}
		} catch (Exception e) {
			logger.info("Database schema initialize or update failed! the failed message is {}",
					e.getLocalizedMessage());
			System.exit(-1);
		}
	}
}
