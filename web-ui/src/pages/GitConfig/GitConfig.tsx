import React from "react";
import { BlankState, Button, Loading } from "../../webui";
import { useGetStatusQuery, useInitMutation } from "./GitConfig.service";
import RemoteSync from "./RemoteSync";
import HistoryList from "./HistoryList";
import "./_styles.scss";

const GitConfig = () => {
  const { data, isLoading } = useGetStatusQuery();
  const [init, { isLoading: initing }] = useInitMutation();

  const initialized = !!(data && data.initialized);

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
            content="Track and restore gateway configuration changes in git. Initializing creates a repository in the data directory and commits the current configuration."
            primaryButton={
              <Button
                colorClass="primary"
                size="large"
                disabled={initing}
                onClick={() => init()}
              >
                {initing ? "Initializing…" : "Initialize versioning"}
              </Button>
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
