/*
 * Copyright 2010, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.dao;

import org.hibernate.Session;
import javax.inject.Named;
import org.zanata.model.HAccountResetPasswordKey;

@Named("accountResetPasswordKeyDAO")

@javax.enterprise.context.Dependent
public class AccountResetPasswordKeyDAO extends
        AbstractDAOImpl<HAccountResetPasswordKey, String> {

    public AccountResetPasswordKeyDAO() {
        super(HAccountResetPasswordKey.class);
    }

    public AccountResetPasswordKeyDAO(Session session) {
        super(HAccountResetPasswordKey.class, session);
    }

    public HAccountResetPasswordKey findByAccount(Long accountId) {
        return (HAccountResetPasswordKey) getSession()
            .createQuery(
                "from HAccountResetPasswordKey key where key.account.id = :accountId")
            .setLong("accountId", accountId)
            .setComment("AccountResetPasswordKeyDAO.findByAccount").uniqueResult();
    }

    public String getUsername(String keyHash) {
        return (String) getSession()
            .createQuery(
                "select key.account.username from HAccountResetPasswordKey key where key.keyHash = :keyHash")
            .setParameter("keyHash", keyHash)
            .setComment("AccountResetPasswordKeyDAO.getUsername").uniqueResult();
    }
}
