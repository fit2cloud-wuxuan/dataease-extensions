package io.dataease.plugins.datasource.hiveV1.provider;

import com.google.gson.Gson;
import io.dataease.plugins.common.base.domain.DeDriver;
import io.dataease.plugins.common.base.mapper.DeDriverMapper;
import io.dataease.plugins.common.constants.DatasourceTypes;
import io.dataease.plugins.common.dto.datasource.TableDesc;
import io.dataease.plugins.common.dto.datasource.TableField;
import io.dataease.plugins.common.exception.DataEaseException;
import io.dataease.plugins.common.request.datasource.DatasourceRequest;
import io.dataease.plugins.datasource.entity.JdbcConfiguration;
import io.dataease.plugins.datasource.provider.DefaultJdbcProvider;
import io.dataease.plugins.datasource.provider.ExtendedJdbcClassLoader;
import io.dataease.plugins.datasource.query.QueryProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;


@Component()
public class HiveV1DsProvider extends DefaultJdbcProvider {
    @Resource
    private DeDriverMapper deDriverMapper;

    @Override
    public String getType() {
        return "hiveV1";
    }

    @Override
    public boolean isUseDatasourcePool() {
        return false;
    }

    @Override
    public Connection getConnection(DatasourceRequest datasourceRequest) throws Exception {
        HiveV1Config dmConfig = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), HiveV1Config.class);

        String defaultDriver = dmConfig.getDriver();
        String customDriver = dmConfig.getCustomDriver();

        String url = dmConfig.getJdbc();
        Properties props = new Properties();
        DeDriver deDriver = null;
        if (StringUtils.isNotEmpty(dmConfig.getAuthMethod()) && dmConfig.getAuthMethod().equalsIgnoreCase("kerberos")) {
            System.setProperty("java.security.krb5.conf", "/opt/dataease/conf/krb5.conf");
            ExtendedJdbcClassLoader classLoader;
            if (isDefaultClassLoader(customDriver)) {
                classLoader = extendedJdbcClassLoader;
            } else {
                deDriver = deDriverMapper.selectByPrimaryKey(customDriver);
                classLoader = getCustomJdbcClassLoader(deDriver);
            }
            Class<?> ConfigurationClass = classLoader.loadClass("org.apache.hadoop.conf.Configuration");
            Method set = ConfigurationClass.getMethod("set", String.class, String.class);
            Object obj = ConfigurationClass.newInstance();
            set.invoke(obj, "hadoop.security.authentication", "Kerberos");

            Class<?> UserGroupInformationClass = classLoader.loadClass("org.apache.hadoop.security.UserGroupInformation");
            Method setConfiguration = UserGroupInformationClass.getMethod("setConfiguration", ConfigurationClass);
            Method loginUserFromKeytab = UserGroupInformationClass.getMethod("loginUserFromKeytab", String.class, String.class);
            setConfiguration.invoke(null, obj);
            loginUserFromKeytab.invoke(null, dmConfig.getUsername(), "/opt/dataease/conf/" + dmConfig.getPassword());
        } else {
            if (StringUtils.isNotBlank(dmConfig.getUsername())) {
                props.setProperty("user", dmConfig.getUsername());
                if (StringUtils.isNotBlank(dmConfig.getPassword())) {
                    props.setProperty("password", dmConfig.getPassword());
                }
            }
        }

        Connection conn;
        String driverClassName;
        ExtendedJdbcClassLoader jdbcClassLoader;
        if (isDefaultClassLoader(customDriver)) {
            driverClassName = defaultDriver;
            jdbcClassLoader = extendedJdbcClassLoader;
        } else {
            if (deDriver == null) {
                deDriver = deDriverMapper.selectByPrimaryKey(customDriver);
            }
            driverClassName = deDriver.getDriverClass();
            jdbcClassLoader = getCustomJdbcClassLoader(deDriver);
        }

        Driver driverClass = (Driver) jdbcClassLoader.loadClass(driverClassName).newInstance();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(jdbcClassLoader);
            conn = driverClass.connect(url, props);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
        return conn;
    }

    @Override
    public List<TableDesc> getTables(DatasourceRequest datasourceRequest) throws Exception {
        List<TableDesc> tables = new ArrayList<>();
        String queryStr = getTablesSql(datasourceRequest);
        JdbcConfiguration jdbcConfiguration = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
        int queryTimeout = jdbcConfiguration.getQueryTimeout() > 0 ? jdbcConfiguration.getQueryTimeout() : 0;
        try (Connection con = getConnectionFromPool(datasourceRequest); Statement statement = getStatement(con, queryTimeout); ResultSet resultSet = statement.executeQuery(queryStr)) {
            while (resultSet.next()) {
                tables.add(getTableDesc(datasourceRequest, resultSet));
            }
        } catch (Exception e) {
            DataEaseException.throwException(e);
        }

        return tables;
    }

    private TableDesc getTableDesc(DatasourceRequest datasourceRequest, ResultSet resultSet) throws SQLException {
        TableDesc tableDesc = new TableDesc();
        tableDesc.setName(resultSet.getString(1));
        return tableDesc;
    }

    @Override
    public List<TableField> getTableFields(DatasourceRequest datasourceRequest) throws Exception {
        List<TableField> list = new LinkedList<>();
        try (Connection connection = getConnectionFromPool(datasourceRequest)) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet resultSet = databaseMetaData.getColumns(null, "%", datasourceRequest.getTable(), "%");
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                String database = resultSet.getString("TABLE_CAT");
                if (database != null) {
                    if (tableName.equals(datasourceRequest.getTable()) && database.equalsIgnoreCase(getDatabase(datasourceRequest))) {
                        TableField tableField = getTableFiled(resultSet, datasourceRequest);
                        list.add(tableField);
                    }
                } else {
                    if (tableName.equals(datasourceRequest.getTable())) {
                        TableField tableField = getTableFiled(resultSet, datasourceRequest);
                        list.add(tableField);
                    }
                }

            }
            resultSet.close();
        } catch (SQLException e) {
            DataEaseException.throwException(e);
        } catch (Exception e) {
            DataEaseException.throwException("Data source connection exception:" + e.getMessage());
        }
        return list;
    }

    private String getDatabase(DatasourceRequest datasourceRequest) {
        JdbcConfiguration jdbcConfiguration = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
        return jdbcConfiguration.getDataBase();
    }


    private TableField getTableFiled(ResultSet resultSet, DatasourceRequest datasourceRequest) throws SQLException {
        TableField tableField = new TableField();
        String colName = resultSet.getString("COLUMN_NAME");
        tableField.setFieldName(colName);
        String remarks = resultSet.getString("REMARKS");
        if (remarks == null || remarks.equals("")) {
            remarks = colName;
        }
        tableField.setRemarks(remarks);
        String dbType = resultSet.getString("TYPE_NAME").toUpperCase();
        tableField.setFieldType(dbType);
        if (dbType.equalsIgnoreCase("LONG")) {
            tableField.setFieldSize(65533);
        }
        if (StringUtils.isNotEmpty(dbType) && dbType.toLowerCase().contains("date") && tableField.getFieldSize() < 50) {
            tableField.setFieldSize(50);
        }
        tableField.setFieldSize(1);
        if (StringUtils.isNotEmpty(tableField.getFieldType()) && tableField.getFieldType().equalsIgnoreCase("DECIMAL")) {
            tableField.setAccuracy(Integer.valueOf(resultSet.getString("DECIMAL_DIGITS")));
        }
        return tableField;
    }

    @Override
    public String checkStatus(DatasourceRequest datasourceRequest) throws Exception {
        String queryStr = getTablesSql(datasourceRequest);
        JdbcConfiguration jdbcConfiguration = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
        int queryTimeout = jdbcConfiguration.getQueryTimeout() > 0 ? jdbcConfiguration.getQueryTimeout() : 0;
        try (Connection con = getConnection(datasourceRequest); Statement statement = getStatement(con, queryTimeout); ResultSet resultSet = statement.executeQuery(queryStr)) {
        } catch (Exception e) {
            e.printStackTrace();
            DataEaseException.throwException(e.getMessage());
        }
        return "Success";
    }


    @Override
    public String getTablesSql(DatasourceRequest datasourceRequest) throws Exception {
        return "show tables";
    }

    @Override
    public String getSchemaSql(DatasourceRequest datasourceRequest) {
        return "show tables";
    }

    @Override
    public Map<String, List> fetchResultAndField(DatasourceRequest datasourceRequest) throws Exception {
        Map<String, List> result = new HashMap<>();
        List<String[]> dataList;
        List<TableField> fieldList;
        JdbcConfiguration jdbcConfiguration = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
        try (Connection connection = getConnectionFromPool(datasourceRequest); Statement stat = connection.createStatement(); ResultSet rs = stat.executeQuery(datasourceRequest.getQuery())) {
            fieldList = fetchResultField(rs, datasourceRequest);
            result.put("fieldList", fieldList);
            dataList = getDataResult(rs, datasourceRequest);
            result.put("dataList", dataList);
            return result;
        } catch (SQLException e) {
            DataEaseException.throwException(e);
        } catch (Exception e) {
            DataEaseException.throwException(e);
        }
        return new HashMap<>();
    }

    private List<TableField> fetchResultField(ResultSet rs, DatasourceRequest datasourceRequest) throws Exception {
        List<TableField> fieldList = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int j = 0; j < columnCount; j++) {
            String f = metaData.getColumnName(j + 1);
            String l = StringUtils.isNotEmpty(metaData.getColumnLabel(j + 1)) ? metaData.getColumnLabel(j + 1) : f;
            String t = metaData.getColumnTypeName(j + 1);
            if ((datasourceRequest.getDatasource().getType().equalsIgnoreCase(DatasourceTypes.hive.name()) || datasourceRequest.getDatasource().getType().equalsIgnoreCase(getType())) && l.contains(".")) {
                l = l.split("\\.")[1];
            }
            TableField field = new TableField();
            field.setFieldName(l);
            field.setRemarks(l);
            field.setFieldType(t);
            field.setFieldSize(metaData.getColumnDisplaySize(j + 1));
            if (t.equalsIgnoreCase("LONG")) {
                field.setFieldSize(65533);
            } //oracle LONG
            if (StringUtils.isNotEmpty(t) && t.toLowerCase().contains("date") && field.getFieldSize() < 50) {
                field.setFieldSize(50);
            }
            fieldList.add(field);
        }
        return fieldList;
    }

    private List<String[]> getDataResult(ResultSet rs, DatasourceRequest datasourceRequest) throws Exception {
        String charset = null;
        String targetCharset = "UTF-8";
        if (datasourceRequest != null && datasourceRequest.getDatasource().getType().equalsIgnoreCase("oracle")) {
            JdbcConfiguration jdbcConfiguration = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
            if (StringUtils.isNotEmpty(jdbcConfiguration.getCharset()) && !jdbcConfiguration.getCharset().equalsIgnoreCase("Default")) {
                charset = jdbcConfiguration.getCharset();
            }
            if (StringUtils.isNotEmpty(jdbcConfiguration.getTargetCharset()) && !jdbcConfiguration.getTargetCharset().equalsIgnoreCase("Default")) {
                targetCharset = jdbcConfiguration.getTargetCharset();
            }
        }
        List<String[]> list = new LinkedList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (rs.next()) {
            String[] row = new String[columnCount];
            for (int j = 0; j < columnCount; j++) {
                int columnType = metaData.getColumnType(j + 1);
                switch (columnType) {
                    case Types.DATE:
                        if (rs.getDate(j + 1) != null) {
                            row[j] = rs.getDate(j + 1).toString();
                        }
                        break;
                    case Types.BOOLEAN:
                        row[j] = rs.getBoolean(j + 1) ? "1" : "0";
                        break;
                    default:
                        if (metaData.getColumnTypeName(j + 1).toLowerCase().equalsIgnoreCase("blob")) {
                            row[j] = rs.getBlob(j + 1) == null ? "" : rs.getBlob(j + 1).toString();
                        } else {
                            if (charset != null && StringUtils.isNotEmpty(rs.getString(j + 1))) {
                                String originStr = new String(rs.getString(j + 1).getBytes(charset), targetCharset);
                                row[j] = new String(originStr.getBytes("UTF-8"), "UTF-8");
                            } else {
                                row[j] = rs.getString(j + 1);
                            }
                        }

                        break;
                }
            }
            list.add(row);
        }
        return list;
    }

    @Override
    public List<TableField> fetchResultField(DatasourceRequest datasourceRequest) throws Exception {
        JdbcConfiguration jdbcConfiguration = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
        int queryTimeout = jdbcConfiguration.getQueryTimeout() > 0 ? jdbcConfiguration.getQueryTimeout() : 0;
        try (Connection connection = getConnectionFromPool(datasourceRequest); Statement stat = connection.createStatement(); ResultSet rs = stat.executeQuery(datasourceRequest.getQuery())) {
            return fetchResultField(rs, datasourceRequest);
        } catch (SQLException e) {
            DataEaseException.throwException(e);
        } catch (Exception e) {
            e.printStackTrace();
            DataEaseException.throwException(e.getMessage());
        }
        return new ArrayList<>();
    }
}
