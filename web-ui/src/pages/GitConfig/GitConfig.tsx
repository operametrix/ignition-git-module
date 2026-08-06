import React from "react";
import { Button, Loading } from "../../webui";
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
        <section className="gitcfg-section">
          <div className="gitcfg-section-header">
            <h3>Get started</h3>
          </div>
          <p className="gitcfg-intro">
            Track all gateway configuration changes in git and restore any
            committed version. Initializing creates a repository in the gateway
            data directory and records a baseline commit of the current
            configuration.
          </p>
          <Button
            colorClass="primary"
            disabled={initing}
            onClick={() => init()}
          >
            {initing ? "Initializing…" : "Initialize config versioning"}
          </Button>
        </section>
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
