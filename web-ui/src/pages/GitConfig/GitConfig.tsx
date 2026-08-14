import React from "react";
import {
  BlankState,
  Button,
  Loading,
  useToastNotifications,
} from "../../webui";
import {
  useGetStatusQuery,
  useGetRemoteQuery,
  useInitMutation,
  useUpdateFromRemoteMutation,
} from "./GitConfig.service";
import { errorToast } from "./errors";
import RemoteSync from "./RemoteSync";
import HistoryList from "./HistoryList";
import "./_styles.scss";

const GitConfig = () => {
  const { data, isLoading } = useGetStatusQuery();
  const [init, { isLoading: initing }] = useInitMutation();
  const { data: remote } = useGetRemoteQuery();
  const [updateFromRemote, { isLoading: updating }] =
    useUpdateFromRemoteMutation();
  const toasts = useToastNotifications();

  const initialized = !!(data && data.initialized);
  // A remote record survives a gateway-backup restore even though .git does not, so its presence
  // on the uninitialized screen means "re-attach + recover from the remote" is possible.
  const remoteConfigured = !!(remote && remote.configured);

  const renderBody = () => {
    if (isLoading) {
      return <Loading isLoading={true} />;
    }

    if (!initialized) {
      return (
        <div className="gitcfg-blank-wrap">
          <BlankState
            className="gitcfg-blank"
            icon={
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 -960 960 960"
                width="120"
                height="120"
                fill="#98a2b3"
                style={{ display: "block" }}
              >
                <path d="M480-120q-138 0-240.5-91.5T122-440h82q14 104 92.5 172T480-200q117 0 198.5-81.5T760-480q0-117-81.5-198.5T480-760q-69 0-129 32t-101 88h110v80H120v-240h80v94q51-64 124.5-99T480-840q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-480q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-120Zm112-192L440-464v-216h80v184l128 128-56 56Z" />
              </svg>
            }
            label="Config versioning is not initialized"
            content={
              remoteConfigured
                ? "A remote is configured but the local repository is missing (e.g. after a gateway-backup restore). Update from remote to re-attach and bring config to the latest committed version, or initialize a fresh repository from the current on-disk configuration."
                : "Track and restore gateway configuration changes in git. Initializing creates a repository in the data directory and commits the current configuration."
            }
            primaryButton={
              <div style={{ display: "flex", gap: "0.75rem" }}>
                {remoteConfigured ? (
                  <Button
                    colorClass="primary"
                    size="large"
                    disabled={updating}
                    onClick={() =>
                      updateFromRemote()
                        .unwrap()
                        .catch(errorToast(toasts, "Update from remote failed"))
                    }
                  >
                    {updating ? "Updating…" : "Update from remote"}
                  </Button>
                ) : null}
                <Button
                  colorClass={remoteConfigured ? "secondary" : "primary"}
                  size="large"
                  disabled={initing}
                  onClick={() =>
                    init()
                      .unwrap()
                      .catch(errorToast(toasts, "Initialize failed"))
                  }
                >
                  {initing ? "Initializing…" : "Initialize versioning"}
                </Button>
              </div>
            }
          />
        </div>
      );
    }

    return <HistoryList />;
  };

  return (
    <div className="gitcfg">
      <RemoteSync initialized={initialized} />
      <div className="gitcfg-content">{renderBody()}</div>
    </div>
  );
};

export default GitConfig;
