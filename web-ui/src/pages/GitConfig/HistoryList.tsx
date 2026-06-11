import React, { useCallback, useMemo, useState } from "react";
import { DataGrid, Modal, Chip } from "../../webui";
import { useGetHistoryQuery, useRestoreMutation } from "./GitConfig.service";
import CommitResources from "./CommitResources";

// DataGrid custom cell: short hash (monospace) plus any ref names as a chip.
const HashCell = ({ row, cell }: any) => {
  const refs = row && row.originalValue ? row.originalValue.refs : "";
  return (
    <span className="gitcfg-mono">
      {cell.getValue()}
      {refs ? (
        <>
          {" "}
          <Chip colorClass="info" alt>
            {refs}
          </Chip>
        </>
      ) : null}
    </span>
  );
};

const COLUMNS = [
  { fieldName: "shortHash", header: "Commit", width: 220, cell: HashCell },
  { fieldName: "author", header: "Author", width: 160 },
  { fieldName: "date", header: "Date", width: 160, sortable: true },
  { fieldName: "message", header: "Message" },
];

// DataGrid invokes rowExpand as rowExpand({ rowData: <originalRow> }); read the hash from there
// (with defensive fallbacks for other shapes).
const resolveHash = (props: any): string =>
  (props && props.rowData && props.rowData.hash) ||
  (props && props.originalValue && props.originalValue.hash) ||
  (props && props.hash) ||
  "";

const HistoryList = () => {
  // Commits now happen gateway-side (auto-commit), so poll to notice them. Subscribe to `data`
  // only: a poll returning identical content keeps the same reference (structural sharing) and
  // must not re-render — the hook's isFetching flips would otherwise re-render the grid every
  // cycle, and DataGrid resets its row model when callback props change identity (the flicker).
  const { data } = useGetHistoryQuery(
    { skip: 0, limit: 200 },
    {
      pollingInterval: 15000,
      selectFromResult: ({ data: d }) => ({ data: d }),
    }
  );
  const [restore, { isLoading: restoring }] = useRestoreMutation();
  const [, setQuery] = useState("");
  const [confirm, setConfirm] = useState<{
    hash: string;
    shortHash: string;
  } | null>(null);

  const commits = data ? data.commits : [];

  // Stable identities so re-renders (restore dialog, real data changes) don't reset the grid.
  const showMore = useMemo(
    () => [
      {
        text: "Restore this version",
        onClick: (rowData: any) =>
          setConfirm({ hash: rowData.hash, shortHash: rowData.shortHash }),
      },
    ],
    []
  );

  const rowExpand = useCallback(
    (props: any) => <CommitResources hash={resolveHash(props)} />,
    []
  );

  const doRestore = async () => {
    if (!confirm) {
      return;
    }
    const hash = confirm.hash;
    setConfirm(null);
    await restore({ hash }).unwrap();
  };

  return (
    <section className="gitcfg-section">
      <div className="gitcfg-section-header">
        <h3>History</h3>
      </div>
      <DataGrid
        id="git-config-history"
        itemName="Commit"
        columnDefs={COLUMNS}
        data={commits}
        uniqueDataKey="hash"
        globalSearch
        denseRows
        setTableQueryParams={setQuery}
        rowExpand={rowExpand}
        showMore={showMore}
        noResultsText="No commits yet."
      />
      <Modal
        open={!!confirm}
        type="confirm"
        title="Restore configuration"
        modalConfig={{
          descriptionText: confirm
            ? `Restore the gateway configuration to ${confirm.shortHash}? This rewrites config on disk to exactly this version — any uncommitted config changes (including new, untracked files) are discarded — and applies it to the running gateway immediately, so live resources (database/device connections, etc.) may briefly drop. Inline encrypted secrets are tied to this gateway's encryption key.`
            : "",
          primaryText: restoring ? "Restoring…" : "Restore",
          secondaryText: "Cancel",
          warningGeneral: true,
          primaryDisabled: restoring,
        }}
        onClose={() => setConfirm(null)}
        onConfirm={doRestore}
      />
    </section>
  );
};

export default HistoryList;
