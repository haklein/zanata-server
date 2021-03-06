package org.zanata.action;

import java.io.Serializable;

import javax.persistence.EntityManager;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;
import org.apache.deltaspike.jpa.api.transaction.Transactional;
import org.hibernate.validator.constraints.NotEmpty;
import javax.inject.Inject;
import javax.inject.Named;

import org.zanata.dao.AccountResetPasswordKeyDAO;
import org.zanata.exception.AuthorizationException;
import org.zanata.exception.NotLoggedInException;
import org.zanata.ApplicationConfiguration;
import org.zanata.exception.KeyNotFoundException;
import org.zanata.i18n.Messages;
import org.zanata.model.HAccountResetPasswordKey;
import org.zanata.seam.security.AbstractRunAsOperation;
import org.zanata.seam.security.IdentityManager;
import org.zanata.ui.faces.FacesMessages;

@Named("passwordReset")
@org.apache.deltaspike.core.api.scope.ViewAccessScoped /* TODO [CDI] check this: migrated from ScopeType.CONVERSATION */
public class PasswordResetAction implements Serializable {

    private static final long serialVersionUID = -3966625589007754411L;

    @Inject
    private EntityManager entityManager;

    @Inject
    private IdentityManager identityManager;

    @Inject
    private FacesMessages facesMessages;

    @Inject
    private Messages msgs;

    @Inject
    private AccountResetPasswordKeyDAO accountResetPasswordKeyDAO;

    @Inject
    private ApplicationConfiguration applicationConfiguration;

    @Getter
    private String activationKey;

    @Getter
    @Setter
    @NotEmpty
    @Size(min = 6, max = 1024)
    private String password;

    @Getter
    private String passwordConfirm;

    @Getter
    private HAccountResetPasswordKey key;


    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
        validatePasswordsMatch();
    }

    public boolean validatePasswordsMatch() {
        if (password == null || !password.equals(passwordConfirm)) {
            facesMessages.addToControl("passwordConfirm",
                    "Passwords do not match");
            return false;
        }
        return true;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
        key =
                entityManager.find(HAccountResetPasswordKey.class,
                        getActivationKey());
    }

    // @Begin(join = true) /* TODO [CDI] commented out begin conversation. Verify it still works properly */
    public void validateActivationKey() {
        if (!applicationConfiguration.isInternalAuth()) {
            throw new AuthorizationException(
                    "Password reset is only available for server using internal authentication");
        }

        if (getActivationKey() == null)
            throw new KeyNotFoundException();

        key =
                entityManager.find(HAccountResetPasswordKey.class,
                        getActivationKey());

        if (key == null)
            throw new KeyNotFoundException();
    }

    private boolean passwordChanged;

    @Transactional
    public String changePassword() {
        // need to get username from DAO due to lazy loading of account in key
        String username =
            accountResetPasswordKeyDAO.getUsername(key.getKeyHash());
        if (!validatePasswordsMatch())
            return null;

        key = entityManager
            .find(HAccountResetPasswordKey.class, getActivationKey());
        entityManager.remove(key);
        entityManager.flush();

        new AbstractRunAsOperation() {
            public void execute() {
                try {
                    passwordChanged =
                        identityManager.changePassword(username, getPassword());
                } catch (AuthorizationException | NotLoggedInException e) {
                    passwordChanged = false;
                    facesMessages.addGlobal(
                        "Error changing password: " + e.getMessage());
                }
            }
        }.addRole("admin").run();

        if (passwordChanged) {
            facesMessages
                    .addGlobal(msgs.get("jsf.password.change.success"));
            return "/account/login.xhtml";
        } else {
            facesMessages
                    .addGlobal(msgs.get("jsf.password.change.failed"));
            return null;
        }

    }

}
