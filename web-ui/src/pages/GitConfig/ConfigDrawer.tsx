import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { DeleteGw } from "@inductiveautomation/ignition-icons";
import {
  Button,
  Card,
  Drawer,
  DrawerTemplate,
  DrawerTemplateSize,
  DrawerTemplateColorTheme,
  Form,
  FormControlInput,
  Modal,
  Radio,
  TextArea,
  TextAutocomplete,
  TextInput,
} from "../../webui";
import {
  useGetRemoteQuery,
  useGetSecretProvidersQuery,
  useSaveRemoteMutation,
  useRemoveRemoteMutation,
  useTestRemoteMutation,
  useDeinitMutation,
} from "./GitConfig.service";

const DEFAULT_BRANCH = "main";

const errText = (e: any): string =>
  (e && e.data && e.data.error) || "Request failed";

interface FormShape {
  uri: string;
  branch: string;
  mode: "inline" | "reference";
  key: string; // embedded SSH private key
  password: string; // embedded HTTPS password/token
  username: string; // HTTPS username
  provider: string; // referenced provider
  secretName: string; // referenced secret name
}

// The "Configure Versioning" lateral panel. The remote's secret is entered/referenced inline in
// the drawer (no credential dropdown, no popup): choose Embedded or Referenced with a radio, then
// either type the secret or point at a gateway Secret Provider secret. The gateway owns a single
// dedicated credential record for the config remote (auto-named), so no name is asked for.
const ConfigDrawer = ({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) => {
  const { data: remote } = useGetRemoteQuery();
  const { data: providersResp } = useGetSecretProvidersQuery();
  const [saveRemote, { isLoading: saving }] = useSaveRemoteMutation();
  const [removeRemote] = useRemoveRemoteMutation();
  const [testRemote, { isLoading: testing }] = useTestRemoteMutation();
  const [deinit] = useDeinitMutation();

  const form = useForm<FormShape>({
    defaultValues: {
      uri: "",
      branch: DEFAULT_BRANCH,
      mode: "inline",
      key: "",
      password: "",
      username: "",
      provider: "",
      secretName: "",
    },
  });
  const { watch, setValue, getValues, reset, handleSubmit } = form;
  const uri = watch("uri");
  const mode = watch("mode");
  const key = watch("key");
  const password = watch("password");
  const provider = watch("provider");
  const secretName = watch("secretName");

  const [feedback, setFeedback] = useState<{
    ok: boolean;
    text: string;
  } | null>(null);
  const [confirmRemoveRemote, setConfirmRemoveRemote] = useState(false);
  const [confirmDeinit, setConfirmDeinit] = useState(false);

  // Prefill from the saved remote each time the drawer opens (embedded secrets stay blank).
  useEffect(() => {
    if (!open || !remote) {
      return;
    }
    reset({
      uri: remote.uri || "",
      branch: remote.branch || DEFAULT_BRANCH,
      mode: remote.secretMode || "inline",
      key: "",
      password: "",
      username: remote.username || "",
      provider: remote.providerName || "",
      secretName: remote.secretName || "",
    });
    setFeedback(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const configured = !!(remote && remote.configured);
  const sshUri = !!uri && !uri.trim().toLowerCase().startsWith("http");
  const credType: "SSH" | "HTTPS" = sshUri ? "SSH" : "HTTPS";

  const providers = providersResp ? providersResp.providers : [];
  const providerNames = providers.map((p) => p.name);
  const noProviders = providerNames.length === 0;
  const secretNames = providers.find((p) => p.name === provider)?.secrets || [];

  // A secret is available to save when: referencing (provider + name), typing a new embedded
  // secret, or editing an already-configured remote (blank embedded secret keeps the current one).
  const hasSecret =
    mode === "reference"
      ? !!provider && !!secretName
      : (credType === "SSH" ? !!key : !!password) || configured;
  const saveEnabled = !!uri.trim() && hasSecret;

  const secretPayload = (d: FormShape) => ({
    uri: d.uri.trim(),
    mode: d.mode,
    key: d.key,
    password: d.password,
    username: d.username.trim(),
    providerName: d.provider,
    secretName: d.secretName,
  });

  const onSave = handleSubmit(async (d) => {
    setFeedback(null);
    try {
      await saveRemote({
        ...secretPayload(d),
        branch: d.branch.trim() || DEFAULT_BRANCH,
      }).unwrap();
      onClose();
    } catch (e: any) {
      setFeedback({ ok: false, text: errText(e) });
    }
  });

  const doTest = async () => {
    setFeedback(null);
    try {
      await testRemote(secretPayload(getValues())).unwrap();
      setFeedback({ ok: true, text: "Connection OK." });
    } catch (e: any) {
      setFeedback({ ok: false, text: errText(e) });
    }
  };

  const keepHint = configured ? " (leave blank to keep current)" : "";

  const drawerBody = (
    <Form context={form} className="gitcfg-form">
      <Card title="Remote">
        <FormControlInput
          name="uri"
          id="cfg-uri"
          label="Repository URI"
          description="SSH or HTTPS URL of the config repository's remote."
          input={
            <TextInput placeholder="ssh://git@host/org/gateway-config.git" />
          }
        />
        <FormControlInput
          name="branch"
          id="cfg-branch"
          label="Branch"
          description="Remote branch to push to."
          input={<TextInput placeholder={DEFAULT_BRANCH} />}
        />
        <FormControlInput
          name="mode"
          id="cfg-mode"
          label="Secret"
          input={
            <Radio
              name="cfg-mode"
              value={mode}
              radios={[
                { label: "Embedded", value: "inline" },
                { label: "Referenced", value: "reference" },
              ]}
              onChange={(_e: any, value: string) =>
                setValue("mode", value as "inline" | "reference")
              }
            />
          }
        />
        {credType === "HTTPS" ? (
          <FormControlInput
            name="username"
            id="cfg-username"
            label="Username"
            input={<TextInput />}
          />
        ) : null}
        {mode === "inline" ? (
          <FormControlInput
            name={credType === "SSH" ? "key" : "password"}
            id="cfg-secret"
            label={
              (credType === "SSH" ? "Private key" : "Password / token") +
              keepHint
            }
            input={
              credType === "SSH" ? (
                <TextArea placeholder="-----BEGIN OPENSSH PRIVATE KEY-----" />
              ) : (
                <TextInput type="password" />
              )
            }
          />
        ) : (
          <>
            <FormControlInput
              name="provider"
              id="cfg-provider"
              label="Provider"
              disabled={noProviders}
              otherProps={{ disabled: noProviders }}
              description={
                <span className="gitcfg-desc">
                  Select a secret provider from the available options
                  {noProviders ? (
                    <span className="gitcfg-provider-error">
                      No Providers Exist. Please Create one.
                    </span>
                  ) : null}
                </span>
              }
              input={
                <TextAutocomplete
                  id="cfg-provider-ac"
                  options={providerNames}
                  value={provider}
                  disabled={noProviders}
                  placeholder="Select Secret Provider"
                  onChange={(value: string) => {
                    setValue("provider", value || "");
                    setValue("secretName", "");
                  }}
                />
              }
            />
            <FormControlInput
              name="secretName"
              id="cfg-secretname"
              label="Secret name"
              disabled={noProviders || !provider}
              otherProps={{ disabled: noProviders || !provider }}
              description="Select from the list or manually enter a secret name"
              input={
                <TextAutocomplete
                  id="cfg-secretname-ac"
                  freeSolo
                  options={secretNames}
                  value={secretName}
                  disabled={noProviders || !provider}
                  placeholder="Select or enter a secret name…"
                  onChange={(value: string) =>
                    setValue("secretName", value || "")
                  }
                />
              }
            />
          </>
        )}

        {feedback ? (
          <p className={feedback.ok ? "gitcfg-ok" : "gitcfg-err"}>
            {feedback.text}
          </p>
        ) : null}

        <div className="gitcfg-actions">
          <Button disabled={testing || !saveEnabled} onClick={doTest}>
            {testing ? "Testing…" : "Test connection"}
          </Button>
          {configured ? (
            <Button
              colorClass="secondary"
              onClick={() => setConfirmRemoveRemote(true)}
            >
              Remove remote…
            </Button>
          ) : null}
        </div>
      </Card>

      <Card title="Danger Zone" className="gitcfg-danger-card">
        <p className="gitcfg-muted">
          {"This action will permanently delete the gateway's versioning"}
        </p>
        <Button
          colorClass="destructive"
          startIcon={<DeleteGw width={16} height={16} />}
          onClick={() => setConfirmDeinit(true)}
        >
          Delete versioning
        </Button>
      </Card>
    </Form>
  );

  return (
    <>
      <Drawer
        anchor="right"
        open={open}
        onClose={onClose}
        size={DrawerTemplateSize.SMALL}
      >
        <DrawerTemplate
          title="Configure Versioning"
          path={["Configure Versioning"]}
          theme={DrawerTemplateColorTheme.GREY}
          primaryActionText={saving ? "Saving…" : "Save"}
          secondaryActionText="Cancel"
          primaryDisabled={saving || !saveEnabled}
          onComplete={onSave}
          onCancel={onClose}
          onClose={onClose}
        >
          {drawerBody}
        </DrawerTemplate>
      </Drawer>

      <Modal
        open={confirmRemoveRemote}
        type="confirm"
        title="Remove remote"
        modalConfig={{
          confirmationText:
            "Remove the configured remote? The local history is kept; only the push destination and its credential link are removed.",
          primaryText: "Remove",
          secondaryText: "Cancel",
          warningGeneral: true,
        }}
        onClose={() => setConfirmRemoveRemote(false)}
        onConfirm={() => {
          setConfirmRemoveRemote(false);
          removeRemote();
        }}
      />

      <Modal
        open={confirmDeinit}
        type="confirm"
        title="Delete versioning"
        modalConfig={{
          confirmationText:
            "Removing versioning deletes all version history in the gateway data directory. Saved credentials are kept.",
          primaryText: "Delete versioning",
          secondaryText: "Cancel",
          warningGeneral: true,
        }}
        onClose={() => setConfirmDeinit(false)}
        onConfirm={() => {
          setConfirmDeinit(false);
          deinit();
          onClose();
        }}
      />
    </>
  );
};

export default ConfigDrawer;
