import React, { useState } from "react";
import {
  Button,
  Chip,
  Modal,
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

// Sentinel select values opening the inline add-credential form.
const ADD_CREDENTIAL = "__add__";

const errText = (e: any): string =>
  (e && e.data && e.data.error) || "Request failed";

// Remote configuration for the config repo. Pushing is strictly manual — there is no
// auto-push; the gateway only ever pushes when the user clicks "Push now".
const RemoteCard = () => {
  const { data: remote } = useGetRemoteQuery();
  const { data: creds } = useGetCredentialsQuery();
  const [saveRemote, { isLoading: saving }] = useSaveRemoteMutation();
  const [removeRemote] = useRemoveRemoteMutation();
  const [testRemote, { isLoading: testing }] = useTestRemoteMutation();
  const [push, { isLoading: pushing }] = usePushMutation();
  const [addCredential, { isLoading: addingCred }] = useAddCredentialMutation();

  const [editing, setEditing] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [uri, setUri] = useState("");
  const [branch, setBranch] = useState("");
  // Selected credential as "<TYPE>:<id>", or the add-credential sentinel.
  const [credKey, setCredKey] = useState("");
  const [feedback, setFeedback] = useState<{
    ok: boolean;
    text: string;
  } | null>(null);
  // Inline add-credential form (SSH: name+key; HTTPS: host+username+token).
  const [credName, setCredName] = useState("");
  const [credUser, setCredUser] = useState("");
  const [credSecret, setCredSecret] = useState("");

  if (!remote) {
    return null;
  }

  const sshUri = !!uri && !uri.trim().toLowerCase().startsWith("http");
  const credType = sshUri ? "SSH" : "HTTPS";

  const openEdit = () => {
    setUri(remote.uri || "");
    setBranch(remote.branch || remote.defaultBranch || "");
    if (remote.sshKeyId) {
      setCredKey(`SSH:${remote.sshKeyId}`);
    } else if (remote.httpsCredentialId) {
      setCredKey(`HTTPS:${remote.httpsCredentialId}`);
    } else {
      setCredKey("");
    }
    setFeedback(null);
    setEditing(true);
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

  const doSave = async () => {
    setFeedback(null);
    try {
      await saveRemote({
        uri: uri.trim(),
        branch: branch.trim(),
        ...parsedCred(),
      }).unwrap();
      setEditing(false);
    } catch (e: any) {
      setFeedback({ ok: false, text: errText(e) });
    }
  };

  const renderLastPush = () => {
    if (!remote.lastPush) {
      return <Chip alt>never pushed</Chip>;
    }
    const when = new Date(remote.lastPush.time).toLocaleString();
    return remote.lastPush.ok ? (
      <Chip colorClass="success">last push: {when}</Chip>
    ) : (
      <Chip colorClass="error">
        push failed ({when}): {remote.lastPush.error}
      </Chip>
    );
  };

  const renderForm = () => (
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
          onChange={(e: any) => setCredKey(e.target.value)}
        />
      </label>
      {credKey === ADD_CREDENTIAL ? (
        <div className="gitcfg-addcred">
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
            <span>
              {credType === "SSH" ? "Private key" : "Password / token"}
            </span>
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
          <Button
            disabled={addingCred || !credName.trim() || !credSecret}
            onClick={doAddCredential}
          >
            {addingCred ? "Saving…" : "Save credential"}
          </Button>
        </div>
      ) : null}
      <label className="gitcfg-field">
        <span>Branch</span>
        <TextInput
          placeholder={remote.defaultBranch}
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
          colorClass="primary"
          disabled={saving || !uri.trim() || !credentialPicked()}
          onClick={doSave}
        >
          {saving ? "Saving…" : "Save"}
        </Button>
        <Button
          disabled={testing || !uri.trim() || !credentialPicked()}
          onClick={doTest}
        >
          {testing ? "Testing…" : "Test connection"}
        </Button>
        <Button colorClass="link" onClick={() => setEditing(false)}>
          Cancel
        </Button>
      </div>
    </div>
  );

  const renderSummary = () => (
    <div className="gitcfg-remote-summary">
      <div>
        <span className="gitcfg-mono">{remote.uri}</span>{" "}
        <Chip alt>branch: {remote.branch}</Chip>{" "}
        <Chip alt>{remote.credentialLabel}</Chip> {renderLastPush()}
      </div>
      <div className="gitcfg-actions">
        <Button
          colorClass="primary"
          disabled={pushing}
          onClick={() =>
            push()
              .unwrap()
              .catch(() => undefined)
          }
        >
          {pushing ? "Pushing…" : "Push now"}
        </Button>
        <Button onClick={openEdit}>Edit</Button>
        <Button colorClass="link" onClick={() => setConfirmRemove(true)}>
          Remove
        </Button>
      </div>
    </div>
  );

  return (
    <section className="gitcfg-section">
      <div className="gitcfg-section-header">
        <h3>Remote</h3>
      </div>
      {editing ? (
        renderForm()
      ) : remote.configured ? (
        renderSummary()
      ) : (
        <div className="gitcfg-remote-summary">
          <span className="gitcfg-muted">No remote configured.</span>
          <Button onClick={openEdit}>Configure remote…</Button>
        </div>
      )}
      <Modal
        open={confirmRemove}
        type="confirm"
        title="Remove remote"
        modalConfig={{
          descriptionText:
            "Remove the configured remote? The local history is kept; only the push destination and its credential link are removed.",
          primaryText: "Remove",
          secondaryText: "Cancel",
          warningGeneral: true,
        }}
        onClose={() => setConfirmRemove(false)}
        onConfirm={() => {
          setConfirmRemove(false);
          removeRemote();
        }}
      />
    </section>
  );
};

export default RemoteCard;
