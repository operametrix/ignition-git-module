import React, { useState } from "react";
import { SettingsGw, Refresh } from "@inductiveautomation/ignition-icons";
import { Button, PageHeader } from "../../webui";
import { useGetRemoteQuery, usePushMutation } from "./GitConfig.service";
import ConfigDrawer from "./ConfigDrawer";

// The Versioning page header. A primary "Configure Versioning" button (trailing cog) opens the
// lateral configuration drawer; "Remote Sync" pushes to the remote, with bold text showing how
// many local commits are not yet on the remote. The Sync button is primary only when diverged,
// secondary when up to date. Syncing is strictly manual. The remote query polls so the unsynced
// count stays current as config auto-commits happen.
const RemoteSync = ({ initialized }: { initialized: boolean }) => {
  const { data: remote } = useGetRemoteQuery(undefined, {
    skip: !initialized,
    pollingInterval: 15000,
  });
  const [push, { isLoading: pushing }] = usePushMutation();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const configured = !!(remote && remote.configured);
  const ahead = remote?.ahead ?? 0;
  const unsynced = configured && ahead > 0;

  const headerActions = initialized ? (
    <div className="gitcfg-header-actions">
      {configured ? (
        <>
          {unsynced ? (
            <span className="gitcfg-sync-status gitcfg-sync-off">
              {ahead} change{ahead === 1 ? "" : "s"} not synced
            </span>
          ) : (
            <span className="gitcfg-sync-status gitcfg-sync-ok">
              ✓ Up to date
            </span>
          )}
          <Button
            colorClass={unsynced ? "primary" : "secondary"}
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
        </>
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
