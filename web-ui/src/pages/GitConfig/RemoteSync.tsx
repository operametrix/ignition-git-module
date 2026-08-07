import React, { useState } from "react";
import { SettingsGw } from "@inductiveautomation/ignition-icons";
import {
  Button,
  Chip,
  Modal,
  PageHeader,
  SelectInput,
  TextArea,
  TextInput,
} from "../../webui";
import {
  useGetRemoteQuery,
  useGetCredentialsQuery,
  useSaveRemoteMutation,
  useRemoveRemoteMutation,
  useTestRemoteMutation,
  usePushMutation,
  useAddCredentialMutation,
} from "./GitConfig.service";

// Sentinel select value opening the add-credential popup.
const ADD_CREDENTIAL = "__add__";

const DEFAULT_BRANCH = "main";

const errText = (e: any): string =>
  (e && e.data && e.data.error) || "Request failed";

type Popup = "none" | "edit" | "addCred" | "remove";

// The Versioning page header: remote synchronization lives here — status chips next to the
// title, Push / remote-settings action buttons, and WebUI popups (Modal) for all editing.
// Pushing is strictly manual; there is no auto-push.
const RemoteSync = ({ initialized }: { initialized: boolean }) => {
  const { data: remote } = useGetRemoteQuery(undefined, { skip: !initialized });
  const { data: creds } = useGetCredentialsQuery(undefined, {
    skip: !initialized,
  });
  const [saveRemote, { isLoading: saving }] = useSaveRemoteMutation();
  const [removeRemote] = useRemoveRemoteMutation();
  const [testRemote, { isLoading: testing }] = useTestRemoteMutation();
  const [push, { isLoading: pushing }] = usePushMutation();
  const [addCredential, { isLoading: addingCred }] = useAddCredentialMutation();

  const [popup, setPopup] = useState<Popup>("none");
  const [uri, setUri] = useState("");
  const [branch, setBranch] = useState(DEFAULT_BRANCH);
  // Selected credential as "<TYPE>:<id>".
  const [credKey, setCredKey] = useState("");
  const [feedback, setFeedback] = useState<{
    ok: boolean;
    text: string;
  } | null>(null);
  // Add-credential popup fields (SSH: name+key; HTTPS: host+username+token).
  const [credName, setCredName] = useState("");
  const [credUser, setCredUser] = useState("");
  const [credSecret, setCredSecret] = useState("");

  const sshUri = !!uri && !uri.trim().toLowerCase().startsWith("http");
  const credType = sshUri ? "SSH" : "HTTPS";

  const openEdit = () => {
    if (!remote) {
      return;
    }
    setUri(remote.uri || "");
    setBranch(remote.branch || DEFAULT_BRANCH);
    if (remote.sshKeyId) {
      setCredKey(`SSH:${remote.sshKeyId}`);
    } else if (remote.httpsCredentialId) {
      setCredKey(`HTTPS:${remote.httpsCredentialId}`);
    } else {
      setCredKey("");
    }
    setFeedback(null);
    setPopup("edit");
  };

  const credOptions = (creds ? creds.credentials : [])
    .filter((c) => c.type === credType)
    .map((c) => ({
      label: `${c.label} (${c.type})`,
      value: `${c.type}:${c.id}`,
    }));
  credOptions.push({ label: "Add credential…", value: ADD_CREDENTIAL });

  const parsedCred = () => {
    const [type, idStr] = credKey.split(":");
    const id = Number(idStr) || 0;
    return {
      sshKeyId: type === "SSH" ? id : 0,
      httpsCredentialId: type === "HTTPS" ? id : 0,
    };
  };

  const credentialPicked = () => {
    const { sshKeyId, httpsCredentialId } = parsedCred();
    return sshKeyId > 0 || httpsCredentialId > 0;
  };

  const doSave = async () => {
    setFeedback(null);
    try {
      await saveRemote({
        uri: uri.trim(),
        branch: branch.trim() || DEFAULT_BRANCH,
        ...parsedCred(),
      }).unwrap();
      setPopup("none");
    } catch (e: any) {
      setFeedback({ ok: false, text: errText(e) });
    }
  };

  const doTest = async () => {
    setFeedback(null);
    try {
      await testRemote({ uri: uri.trim(), ...parsedCred() }).unwrap();
      setFeedback({ ok: true, text: "Connection OK." });
    } catch (e: any) {
      setFeedback({ ok: false, text: errText(e) });
    }
  };

  const doAddCredential = async () => {
    try {
      const result = await addCredential(
        credType === "SSH"
          ? { type: "SSH", name: credName.trim(), key: credSecret }
          : {
              type: "HTTPS",
              host: credName.trim(),
              username: credUser.trim(),
              password: credSecret,
            }
      ).unwrap();
      setCredKey(`${credType}:${result.id}`);
      setCredName("");
      setCredUser("");
      setCredSecret("");
      setFeedback(null);
      setPopup("edit");
    } catch (e: any) {
      setFeedback({ ok: false, text: errText(e) });
    }
  };

  const configured = !!(remote && remote.configured);

  const lastPushStatus =
    configured && remote ? (
      remote.lastPush ? (
        remote.lastPush.ok ? (
          <Chip colorClass="success">
            last push: {new Date(remote.lastPush.time).toLocaleString()}
          </Chip>
        ) : (
          <Chip colorClass="error">push failed: {remote.lastPush.error}</Chip>
        )
      ) : (
        <Chip alt>never pushed</Chip>
      )
    ) : null;

  // Right-aligned header cluster: [last-push status] [Push] [cog]. Rendered as customContent so
  // it sits in the header row; margin-left:auto (in _styles) pushes it flush right, with the
  // datetime to the left of the Push button. The cog is an icon-only Button (children, not
  // startIcon, so MUI doesn't offset it — keeps the glyph centered).
  const headerActions = initialized ? (
    <div className="gitcfg-header-actions">
      {lastPushStatus}
      {configured ? (
        <Button
          colorClass="primary"
          disabled={pushing}
          onClick={() =>
            push()
              .unwrap()
              .catch(() => undefined)
          }
        >
          {pushing ? "Pushing…" : "Push"}
        </Button>
      ) : null}
      <Button
        soloIcon
        colorClass="secondary"
        aria-label={configured ? "Remote settings" : "Configure remote"}
        onClick={openEdit}
      >
        <SettingsGw width={18} height={18} />
      </Button>
    </div>
  ) : null;

  const editForm = (
    <div className="gitcfg-form">
      <label className="gitcfg-field">
        <span>URI</span>
        <TextInput
          placeholder="ssh://git@host/org/gateway-config.git"
          value={uri}
          onChange={(e: any) => setUri(e.target.value)}
        />
      </label>
      <label className="gitcfg-field">
        <span>Credential</span>
        <SelectInput
          values={credOptions}
          value={credKey}
          showPlaceholder
          emptyPlaceHolderText={`Pick a ${credType} credential…`}
          onChange={(e: any) =>
            e.target.value === ADD_CREDENTIAL
              ? setPopup("addCred")
              : setCredKey(e.target.value)
          }
        />
      </label>
      <label className="gitcfg-field">
        <span>Branch</span>
        <TextInput
          placeholder={DEFAULT_BRANCH}
          value={branch}
          onChange={(e: any) => setBranch(e.target.value)}
        />
      </label>
      {feedback ? (
        <p className={feedback.ok ? "gitcfg-ok" : "gitcfg-err"}>
          {feedback.text}
        </p>
      ) : null}
      <div className="gitcfg-actions">
        <Button
          disabled={testing || !uri.trim() || !credentialPicked()}
          onClick={doTest}
        >
          {testing ? "Testing…" : "Test connection"}
        </Button>
        {remote && remote.configured ? (
          <Button colorClass="destructive" onClick={() => setPopup("remove")}>
            Remove remote…
          </Button>
        ) : null}
      </div>
    </div>
  );

  const addCredForm = (
    <div className="gitcfg-form">
      <label className="gitcfg-field">
        <span>{credType === "SSH" ? "Key name" : "Host label"}</span>
        <TextInput
          placeholder={credType === "SSH" ? "deploy-key" : "github.com"}
          value={credName}
          onChange={(e: any) => setCredName(e.target.value)}
        />
      </label>
      {credType === "HTTPS" ? (
        <label className="gitcfg-field">
          <span>Username</span>
          <TextInput
            value={credUser}
            onChange={(e: any) => setCredUser(e.target.value)}
          />
        </label>
      ) : null}
      <label className="gitcfg-field">
        <span>{credType === "SSH" ? "Private key" : "Password / token"}</span>
        {credType === "SSH" ? (
          <TextArea
            placeholder="-----BEGIN OPENSSH PRIVATE KEY-----"
            value={credSecret}
            onChange={(e: any) => setCredSecret(e.target.value)}
          />
        ) : (
          <TextInput
            type="password"
            value={credSecret}
            onChange={(e: any) => setCredSecret(e.target.value)}
          />
        )}
      </label>
      {feedback && !feedback.ok ? (
        <p className="gitcfg-err">{feedback.text}</p>
      ) : null}
    </div>
  );

  return (
    <>
      <PageHeader pageTitle="Versioning" customContent={headerActions} />
      <Modal
        open={popup === "edit"}
        type="primary"
        title={
          remote && remote.configured ? "Remote settings" : "Configure remote"
        }
        modalConfig={{
          content: editForm,
          primaryText: saving ? "Saving…" : "Save",
          secondaryText: "Cancel",
          primaryDisabled: saving || !uri.trim() || !credentialPicked(),
        }}
        onClose={() => setPopup("none")}
        onConfirm={doSave}
      />
      <Modal
        open={popup === "addCred"}
        type="primary"
        title={credType === "SSH" ? "Add SSH key" : "Add HTTPS credential"}
        modalConfig={{
          content: addCredForm,
          primaryText: addingCred ? "Saving…" : "Save credential",
          secondaryText: "Cancel",
          primaryDisabled: addingCred || !credName.trim() || !credSecret,
        }}
        onClose={() => setPopup("edit")}
        onConfirm={doAddCredential}
      />
      <Modal
        open={popup === "remove"}
        type="confirm"
        title="Remove remote"
        modalConfig={{
          descriptionText:
            "Remove the configured remote? The local history is kept; only the push destination and its credential link are removed.",
          primaryText: "Remove",
          secondaryText: "Cancel",
          warningGeneral: true,
        }}
        onClose={() => setPopup("edit")}
        onConfirm={() => {
          setPopup("none");
          removeRemote();
        }}
      />
    </>
  );
};

export default RemoteSync;
