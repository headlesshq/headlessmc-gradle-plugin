package io.github.headlesshq.headlessmc.gradle.internal

import me.earth.headlessmc.launcher.auth.*
import net.raphimc.minecraftauth.step.java.StepMCProfile
import net.raphimc.minecraftauth.step.java.StepMCToken
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession
import java.util.*

internal class GradleAccountManager(
    accountValidator: AccountValidator,
    offlineChecker: OfflineChecker,
    accountStore: AccountStore
) : AccountManager(accountValidator, offlineChecker, accountStore) {
    override fun getPrimaryAccount(): ValidatedAccount {
        return super.getPrimaryAccount() ?: return DUMMY_ACCOUNT
    }

    override fun refreshAccount(account: ValidatedAccount?): ValidatedAccount {
        if (account === DUMMY_ACCOUNT) {
            return DUMMY_ACCOUNT
        }

        return super.refreshAccount(account)
    }

    companion object {
        private val ID: UUID = UUID.fromString("f17a14f2-4379-47d8-91c7-26b4d28a73aa")
        val DUMMY_ACCOUNT =  ValidatedAccount(
            StepFullJavaSession.FullJavaSession(
                StepMCProfile.MCProfile(
                    ID,
                    "Dev",
                    null,
                    StepMCToken.MCToken(
                        "",
                        "",
                        0L,
                        null
                    )
                ),
                null
            ),
            ""
        )
    }

}