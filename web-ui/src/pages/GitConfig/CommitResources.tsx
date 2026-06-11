import React, { useMemo, useState } from "react";
import { Button, Chip, Loading } from "../../webui";
import {
  useGetCommitFilesQuery,
  useLazyGetFileDiffQuery,
} from "./GitConfig.service";
import DiffViewer from "./DiffViewer";
import { groupByResource, displayName } from "./resources";

const fileColor = (t: string) => {
  const u = t.toUpperCase();
  if (u.startsWith("ADD")) return "success";
  if (u.startsWith("DELETE")) return "error";
  if (u.startsWith("RENAME") || u.startsWith("COPY")) return "info";
  return "warning";
};

// Rendered inside a History row's expand area: the gateway resources impacted by that commit,
// click → historical diff of the resource's primary file.
const CommitResources = ({ hash }: { hash: string }) => {
  const { data, isFetching } = useGetCommitFilesQuery(hash, { skip: !hash });
  const [trigger, { data: diff, isFetching: diffLoading }] =
    useLazyGetFileDiffQuery();
  const [activePath, setActivePath] = useState<string | null>(null);

  const resources = useMemo(
    () =>
      data
        ? groupByResource(
            data.files.map((f) => ({ path: f.path, type: f.changeType }))
          )
        : [],
    [data]
  );

  if (isFetching) {
    return <Loading isLoading={true} />;
  }

  return (
    <div className="gitcfg-files">
      <ul>
        {resources.map((r) => (
          <li key={r.resource}>
            <Chip colorClass={fileColor(r.changeType)}>{r.changeType}</Chip>{" "}
            <Button
              colorClass="link"
              onClick={() => {
                setActivePath(r.diffPath);
                trigger({ hash, path: r.diffPath });
              }}
            >
              {displayName(r.resource)}
            </Button>
          </li>
        ))}
      </ul>
      {activePath ? (
        diffLoading ? (
          <Loading isLoading={true} />
        ) : diff ? (
          <DiffViewer path={activePath} oldText={diff.old} newText={diff.new} />
        ) : null
      ) : null}
    </div>
  );
};

export default CommitResources;
