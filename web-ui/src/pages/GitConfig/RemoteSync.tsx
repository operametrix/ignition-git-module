import React, { useState } from "react";
import { SettingsGw, Refresh } from "@inductiveautomation/ignition-icons";
import { Button, PageHeader, Tooltip } from "../../webui";
import { useGetRemoteQuery, usePushMutation } from "./GitConfig.service";
import ConfigDrawer from "./ConfigDrawer";

// The Versioning page header. A primary "Configure Versioning" button (trailing cog) opens the
// lateral configuration drawer; "Remote Sync" pushes to the remote, with the last sync time in its
// tooltip. Syncing is strictly manual — there is no auto-push.
const RemoteSync = ({ initialized }: { initialized: boolean }) => {
  const { data: remote } = useGetRemoteQuery(undefined, { skip: !initialized });
  const [push, { isLoading: pushing }] = usePushMutation();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const configured = !!(remote && remote.configured);

  const syncTooltip = remote?.lastPush
    ? remote.lastPush.ok
      ? `Last sync: ${new Date(remote.lastPush.time).toLocaleString()}`
      : `Last sync failed: ${remote.lastPush.error}`
    : "Never synced";

  const headerActions = initialized ? (
    <div className="gitcfg-header-actions">
      {configured ? (
        <Tooltip
          content={syncTooltip}
          type="simple"
          position="bottom"
          showArrow
        >
          <Button
            colorClass="primary"
            startIcon={<Refresh width={18} height={18} />}
            disabled={pushing}
            onClick={() =>
              push()
                .unwrap()
                .catch(() => undefined)
            }
          >
            {pushing ? "Syncing…" : "Remote Sync"}
          </Button>
        </Tooltip>
      ) : null}
      <Button
        colorClass="primary"
        endIcon={<SettingsGw width={18} height={18} />}
        onClick={() => setDrawerOpen(true)}
      >
        Configure Versioning
      </Button>
    </div>
  ) : null;

  return (
    <>
      <PageHeader pageTitle="Versioning" customContent={headerActions} />
      {initialized ? (
        <ConfigDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
      ) : null}
    </>
  );
};

export default RemoteSync;
