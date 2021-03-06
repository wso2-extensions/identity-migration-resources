/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.is.migration.service.v550.dao;

import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.service.v550.bean.OauthTokenInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.wso2.carbon.is.migration.service.v550.SQLConstants.ADD_ACCESS_TOKEN_HASH_COLUMN;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.ADD_REFRESH_TOKEN_HASH_COLUMN;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_ACCESS_TOKEN_TABLE_DB2SQL;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_ACCESS_TOKEN_TABLE_INFORMIX;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_ACCESS_TOKEN_TABLE_MSSQL;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_ACCESS_TOKEN_TABLE_MYSQL;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_ACCESS_TOKEN_TABLE_ORACLE;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_ALL_TOKENS;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_PAGINATED_TOKENS_WITH_HASHES_OTHER;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.RETRIEVE_PAGINATED_TOKENS_WITH_HASHES_MYSQL;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.UPDATE_ENCRYPTED_ACCESS_TOKEN;
import static org.wso2.carbon.is.migration.service.v550.SQLConstants.UPDATE_PLAIN_TEXT_ACCESS_TOKEN;

/**
 * TokenDAO.
 */
public class TokenDAO {

    private static final String ACCESS_TOKEN_HASH = "ACCESS_TOKEN_HASH";
    private static final String REFRESH_TOKEN_HASH = "REFRESH_TOKEN_HASH";
    private static TokenDAO instance = new TokenDAO();

    private TokenDAO() {

    }

    public static TokenDAO getInstance() {

        return instance;
    }

    public boolean isTokenHashColumnsAvailable(Connection connection) throws SQLException {

        String sql;
        boolean isTokenHashColumnsExist = false;
        if (connection.getMetaData().getDriverName().contains("MySQL") || connection.getMetaData().getDriverName()
                .contains("H2")) {
            sql = RETRIEVE_ACCESS_TOKEN_TABLE_MYSQL;
        } else if (connection.getMetaData().getDatabaseProductName().contains("DB2")) {
            sql = RETRIEVE_ACCESS_TOKEN_TABLE_DB2SQL;
        } else if (connection.getMetaData().getDriverName().contains("MS SQL") || connection.getMetaData()
                .getDriverName().contains("Microsoft")) {
            sql = RETRIEVE_ACCESS_TOKEN_TABLE_MSSQL;
        } else if (connection.getMetaData().getDriverName().contains("PostgreSQL")) {
            sql = RETRIEVE_ACCESS_TOKEN_TABLE_MYSQL;
        } else if (connection.getMetaData().getDriverName().contains("Informix")) {
            // Driver name = "IBM Informix JDBC Driver for IBM Informix Dynamic Server"
            sql = RETRIEVE_ACCESS_TOKEN_TABLE_INFORMIX;
        } else {
            sql = RETRIEVE_ACCESS_TOKEN_TABLE_ORACLE;
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            try {
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet != null) {

                    resultSet.findColumn(ACCESS_TOKEN_HASH);
                    resultSet.findColumn(REFRESH_TOKEN_HASH);
                    isTokenHashColumnsExist = true;

                }
            } catch (SQLException e) {
                isTokenHashColumnsExist = false;
            }
        } catch (SQLException e) {
            isTokenHashColumnsExist = false;
        }
        return isTokenHashColumnsExist;
    }

    public void addAccessTokenHashColumn(Connection connection) throws SQLException {

        try (PreparedStatement preparedStatement = connection.prepareStatement(ADD_ACCESS_TOKEN_HASH_COLUMN)) {
            preparedStatement.executeUpdate();
            //connection.commit();
        }
    }

    public void addRefreshTokenHashColumn(Connection connection) throws SQLException {

        try (PreparedStatement preparedStatement = connection.prepareStatement(ADD_REFRESH_TOKEN_HASH_COLUMN)) {
            preparedStatement.executeUpdate();
        }
    }

    public List<OauthTokenInfo> getAllAccessTokens(Connection connection) throws SQLException {

        List<OauthTokenInfo> oauthTokenInfos = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(RETRIEVE_ALL_TOKENS);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                oauthTokenInfos.add(new OauthTokenInfo(resultSet.getString("ACCESS_TOKEN"),
                        resultSet.getString("REFRESH_TOKEN"), resultSet.getString("TOKEN_ID")));
            }
        }
        return oauthTokenInfos;
    }

    /**
     * Get all tokens and token hashes from DB.
     *
     * @param connection JDBC connection.
     * @return List of access tokens.
     * @throws SQLException If an error occurs while retrieving tokens.
     */
    public List<OauthTokenInfo> getAllAccessTokensWithHash(Connection connection, int offset, int limit)
            throws SQLException {

        String sql;
        boolean mysqlQueryUsed = false;
        if (connection.getMetaData().getDriverName().contains("MySQL")
                // We can't use the similar thing like above with DB2.Check
                // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_rjvjdapi.html#imjcc_rjvjdapi__d70364e1426
                || connection.getMetaData().getDatabaseProductName().contains("DB2")
                || connection.getMetaData().getDriverName().contains("H2")
                || connection.getMetaData().getDriverName().contains("PostgreSQL")) {
            sql = RETRIEVE_PAGINATED_TOKENS_WITH_HASHES_MYSQL;
            mysqlQueryUsed = true;
        } else {
            sql = RETRIEVE_PAGINATED_TOKENS_WITH_HASHES_OTHER;
        }

        List<OauthTokenInfo> oauthTokenInfoList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            // In mysql type queries, limit and offset values are changed.
            if (mysqlQueryUsed) {
                preparedStatement.setInt(1, limit);
                preparedStatement.setInt(2, offset);
            } else {
                preparedStatement.setInt(1, offset);
                preparedStatement.setInt(2, limit);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    OauthTokenInfo tokenInfo = new OauthTokenInfo(resultSet.getString("ACCESS_TOKEN"),
                            resultSet.getString("REFRESH_TOKEN"),
                            resultSet.getString("TOKEN_ID"));
                    tokenInfo.setAccessTokenHash(resultSet.getString("ACCESS_TOKEN_HASH"));
                    tokenInfo.setRefreshTokenHash(resultSet.getString("REFRESH_TOKEN_HASH"));
                    oauthTokenInfoList.add(tokenInfo);
                }
            }
        }
        return oauthTokenInfoList;
    }

    public void updateNewEncryptedTokens(List<OauthTokenInfo> updatedOauthTokenList, Connection connection)
            throws SQLException, MigrationClientException {

        connection.setAutoCommit(false);
        try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_ENCRYPTED_ACCESS_TOKEN)) {
            for (OauthTokenInfo oauthTokenInfo : updatedOauthTokenList) {
                preparedStatement.setString(1, oauthTokenInfo.getAccessToken());
                preparedStatement.setString(2, oauthTokenInfo.getRefreshToken());
                preparedStatement.setString(3, oauthTokenInfo.getAccessTokenHash());
                preparedStatement.setString(4, oauthTokenInfo.getRefreshTokenHash());
                preparedStatement.setString(5, oauthTokenInfo.getTokenId());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new MigrationClientException("SQL error while update new encrypted token ", e);
        }
    }

    /**
     * Method to update acess token table with hash values of access tokens and refresh tokens.
     *
     * @param updatedOauthTokenList list of updated tokens information
     * @param connection            database connection
     * @throws SQLException
     */
    public void updatePlainTextTokens(List<OauthTokenInfo> updatedOauthTokenList, Connection connection)
            throws SQLException, MigrationClientException {

        connection.setAutoCommit(false);
        try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_PLAIN_TEXT_ACCESS_TOKEN)) {
            for (OauthTokenInfo oauthTokenInfo : updatedOauthTokenList) {
                preparedStatement.setString(1, oauthTokenInfo.getAccessTokenHash());
                preparedStatement.setString(2, oauthTokenInfo.getRefreshTokenHash());
                preparedStatement.setString(3, oauthTokenInfo.getTokenId());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new MigrationClientException("SQL error while update plan text token", e);
        }
    }

}
