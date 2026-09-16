// Shared error surfacing for the config-versioning page.
//
// The gateway returns errors as HTTP 500 with { error: "<message>" }. We show them as native
// toast notifications (the platform's ToastContainer is mounted by the gateway app shell). Errors
// are sticky + dismissible rather than auto-closing, so longer, actionable messages — e.g. the
// non-fast-forward push explanation — stay on screen until the user reads and dismisses them.

type ToastApi = { notify: (props: any) => unknown };

const UNKNOWN_HOST_KEY = "UNKNOWN_HOST_KEY:";
const CHANGED_HOST_KEY = "CHANGED_HOST_KEY:";

/** Raw gateway message out of an RTK Query error object, with sensible fallbacks. */
const rawMessage = (e: any): string =>
  (e && e.data && e.data.error) ||
  (e && e.error) ||
  "The operation failed. Check the gateway logs for details.";

/** An SSH server key the gateway has not been told to trust yet. */
export interface HostKeyPrompt {
  host: string;
  keyType: string;
  fingerprint: string;
}

/** Details of an unknown host key to confirm, or null when the error is something else. */
export const unknownHostKey = (e: any): HostKeyPrompt | null => {
  const message = rawMessage(e);
  const at = message.indexOf(UNKNOWN_HOST_KEY);
  if (at < 0) {
    return null;
  }
  const [host, keyType, fingerprint] = message
    .substring(at + UNKNOWN_HOST_KEY.length)
    .split("|");
  return {
    host: host || "",
    keyType: keyType || "",
    fingerprint: fingerprint || "",
  };
};

/**
 * Pull the gateway's message out of an RTK Query error object. The host-key markers are internal
 * signals, so they are rewritten into something a person can act on.
 */
export const errorMessage = (e: any): string => {
  const message = rawMessage(e);
  const changed = message.indexOf(CHANGED_HOST_KEY);
  if (changed >= 0) {
    const [host, fingerprint] = message
      .substring(changed + CHANGED_HOST_KEY.length)
      .split("|");
    return (
      `The SSH server ${host} presented a different host key (${fingerprint}) than the one ` +
      "accepted earlier. Verify the new key with whoever runs the server before connecting again."
    );
  }
  const unknown = unknownHostKey(e);
  if (unknown) {
    return (
      `${unknown.host} is not a known SSH server yet. Open Configure Versioning and accept its ` +
      `host key (${unknown.fingerprint}) to continue.`
    );
  }
  return message;
};

/**
 * Build a `.catch(...)` handler that shows the error as a sticky toast titled `title`.
 * Usage: `mutation().unwrap().catch(errorToast(toasts, "Remote Sync failed"))`.
 */
export const errorToast =
  (toasts: ToastApi, title: string) =>
  (e: any): void => {
    toasts.notify({
      type: "error",
      title,
      message: errorMessage(e),
      autoClose: false,
      isDismissible: true,
    });
  };
