/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.sieve.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition;
import org.apache.james.backends.postgres.quota.PostgresQuotaLimitDAO;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresSieveQuotaDAOTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresDataDefinition.aggregateModules(PostgresQuotaDataDefinition.MODULE));

    private static final Username USERNAME = Username.of("user");
    private static final QuotaSizeLimit QUOTA_SIZE = QuotaSizeLimit.size(15L);

    private PostgresSieveQuotaDAO testee;

    @BeforeEach
    void setup() {
        testee = new PostgresSieveQuotaDAO(new PostgresQuotaCurrentValueDAO(postgresExtension.getDefaultPostgresExecutor()),
            new PostgresQuotaLimitDAO(postgresExtension.getDefaultPostgresExecutor()));
    }

    @Test
    void getQuotaShouldReturnEmptyByDefault() {
        assertThat(testee.getGlobalQuota().block())
            .isEmpty();
    }

    @Test
    void getQuotaUserShouldReturnEmptyByDefault() {
        assertThat(testee.getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    void getQuotaShouldReturnStoredValue() {
        testee.setGlobalQuota(QUOTA_SIZE).block();

        assertThat(testee.getGlobalQuota().block())
            .contains(QUOTA_SIZE);
    }

    @Test
    void getQuotaUserShouldReturnStoredValue() {
        testee.setQuota(USERNAME, QUOTA_SIZE).block();

        assertThat(testee.getQuota(USERNAME).block())
            .contains(QUOTA_SIZE);
    }

    @Test
    void removeQuotaShouldDeleteQuota() {
        testee.setGlobalQuota(QUOTA_SIZE).block();

        testee.removeGlobalQuota().block();

        assertThat(testee.getGlobalQuota().block())
            .isEmpty();
    }

    @Test
    void removeQuotaUserShouldDeleteQuotaUser() {
        testee.setQuota(USERNAME, QUOTA_SIZE).block();

        testee.removeQuota(USERNAME).block();

        assertThat(testee.getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    void removeQuotaShouldWorkWhenNoneStore() {
        testee.removeGlobalQuota().block();

        assertThat(testee.getGlobalQuota().block())
            .isEmpty();
    }

    @Test
    void removeQuotaUserShouldWorkWhenNoneStore() {
        testee.removeQuota(USERNAME).block();

        assertThat(testee.getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    void spaceUsedByShouldReturnZeroByDefault() {
        assertThat(testee.spaceUsedBy(USERNAME).block()).isZero();
    }

    @Test
    void spaceUsedByShouldReturnStoredValue() {
        long spaceUsed = 18L;

        testee.updateSpaceUsed(USERNAME, spaceUsed).block();

        assertThat(testee.spaceUsedBy(USERNAME).block()).isEqualTo(spaceUsed);
    }

    @Test
    void updateSpaceUsedShouldBeAdditive() {
        long spaceUsed = 18L;

        testee.updateSpaceUsed(USERNAME, spaceUsed).block();
        testee.updateSpaceUsed(USERNAME, spaceUsed).block();

        assertThat(testee.spaceUsedBy(USERNAME).block()).isEqualTo(2 * spaceUsed);
    }

    @Test
    void updateSpaceUsedShouldWorkWithNegativeValues() {
        long spaceUsed = 18L;

        testee.updateSpaceUsed(USERNAME, spaceUsed).block();
        testee.updateSpaceUsed(USERNAME, -1 * spaceUsed).block();

        assertThat(testee.spaceUsedBy(USERNAME).block()).isZero();
    }

    @Test
    void resetSpaceUsedShouldResetSpaceWhenNewSpaceIsGreaterThanCurrentSpace() {
        testee.updateSpaceUsed(USERNAME, 10L).block();
        testee.resetSpaceUsed(USERNAME, 15L).block();

        assertThat(testee.spaceUsedBy(USERNAME).block()).isEqualTo(15L);
    }

    @Test
    void resetSpaceUsedShouldResetSpaceWhenNewSpaceIsSmallerThanCurrentSpace() {
        testee.updateSpaceUsed(USERNAME, 10L).block();
        testee.resetSpaceUsed(USERNAME, 9L).block();

        assertThat(testee.spaceUsedBy(USERNAME).block()).isEqualTo(9L);
    }
}
