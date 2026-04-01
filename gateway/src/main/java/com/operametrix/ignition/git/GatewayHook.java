package com.operametrix.ignition.git;

import com.operametrix.ignition.git.commissioning.utils.GitCommissioningUtils;
import com.operametrix.ignition.git.records.GitProjectsConfigRecord;
import com.operametrix.ignition.git.records.GitRemoteCredentialsRecord;
import com.operametrix.ignition.git.records.GitReposUsersRecord;
import com.operametrix.ignition.git.records.GitUserHttpsCredentialRecord;
import com.operametrix.ignition.git.records.GitUserSshKeyRecord;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.clientcomm.ClientReqSession;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class GatewayHook extends AbstractGatewayModuleHook {
    static public String MODULE_NAME = "Git";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private GatewayScriptModule scriptModule;
    public static GatewayContext context;

    @Override
    public void setup(GatewayContext gatewayContext) {
        context = gatewayContext;
        scriptModule = new GatewayScriptModule(context);
        BundleUtil.get().addBundle("bundle_git", getClass(), "bundle_git");
        verifySchema(gatewayContext);

        logger.info("setup()");
    }

    private void verifySchema(GatewayContext context) {
        try {
            context.getSchemaUpdater().updatePersistentRecords(
                    GitProjectsConfigRecord.META,
                    GitReposUsersRecord.META,
                    GitUserSshKeyRecord.META,
                    GitUserHttpsCredentialRecord.META,
                    GitRemoteCredentialsRecord.META);
        } catch (SQLException e) {
            logger.error("Error verifying persistent record schemas for HomeConnect records.", e);
        }
    }

    @Override
    public void startup(LicenseState licenseState) {
        GitCommissioningUtils.loadConfiguration();

        logger.info("startup()");
    }

    @Override
    public void shutdown() {
        logger.info("shutdown()");
    }

    @Override
    public boolean isFreeModule() {
        return true;
    }

    @Override
    public boolean isMakerEditionCompatible() {
        return true;
    }

    @Override
    public Object getRPCHandler(ClientReqSession session, String projectName) {
        return scriptModule;
    }
}
