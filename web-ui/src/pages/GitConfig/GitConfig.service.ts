import baseApi from "../../api/index";
import { BASE } from "../../config";

export interface ConfigChange {
  path: string;
  type: string;
}
export interface StatusResp {
  initialized: boolean;
  dirty: boolean;
  changes: ConfigChange[];
}
export interface Commit {
  hash: string;
  shortHash: string;
  author: string;
  date: string;
  message: string;
  refs: string;
}
export interface HistoryResp {
  commits: Commit[];
  remoteConfigured: boolean;
  // Full hashes the local / remote branch tips point at ("" when none).
  localHead: string;
  remoteHead: string;
  hasMore: boolean;
}
export interface CommitFile {
  changeType: string;
  path: string;
}
export interface CommitFilesResp {
  files: CommitFile[];
}
export interface FileDiffResp {
  old: string;
  new: string;
}
export interface CredentialOption {
  id: number;
  type: "SSH" | "HTTPS";
  label: string;
}
export interface RemoteResp {
  configured: boolean;
  uri?: string;
  branch?: string;
  // Secret prefill (the embedded secret itself is never returned).
  secretMode?: "inline" | "reference";
  username?: string;
  providerName?: string;
  secretName?: string;
  // Local commits not yet on the remote (0 = up to date).
  ahead?: number;
  lastPush?: { time: number; ok: boolean; error?: string };
}
// Inline secret fields shared by save/test requests.
export interface RemoteSecretReq {
  uri: string;
  mode: "inline" | "reference";
  key?: string; // embedded SSH private key
  password?: string; // embedded HTTPS password/token
  username?: string; // HTTPS username
  providerName?: string; // referenced
  secretName?: string; // referenced
}
export interface SecretProvider {
  name: string;
  secrets: string[];
  error?: string;
}
export interface SecretProvidersResp {
  providers: SecretProvider[];
}
// Add-credential request: each secret is either typed inline or a Secret Provider reference.
export type AddCredentialReq =
  | { type: "SSH"; name: string; mode: "inline"; key: string }
  | {
      type: "SSH";
      name: string;
      mode: "reference";
      providerName: string;
      secretName: string;
    }
  | {
      type: "HTTPS";
      host: string;
      username: string;
      mode: "inline";
      password: string;
    }
  | {
      type: "HTTPS";
      host: string;
      username: string;
      mode: "reference";
      providerName: string;
      secretName: string;
    };

export const gitConfigApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getStatus: builder.query<StatusResp, void>({
      query: () => `${BASE}/status`,
      providesTags: ["status"],
    }),
    getHistory: builder.query<HistoryResp, { skip: number; limit: number }>({
      query: ({ skip, limit }) => `${BASE}/history?skip=${skip}&limit=${limit}`,
      providesTags: ["history"],
    }),
    getCommitFiles: builder.query<CommitFilesResp, string>({
      query: (hash) => `${BASE}/commit-files?hash=${encodeURIComponent(hash)}`,
    }),
    // Without a hash the gateway diffs HEAD vs the working tree (uncommitted changes).
    getFileDiff: builder.query<FileDiffResp, { hash?: string; path: string }>({
      query: ({ hash, path }) =>
        hash
          ? `${BASE}/file-diff?hash=${encodeURIComponent(
              hash
            )}&path=${encodeURIComponent(path)}`
          : `${BASE}/file-diff?path=${encodeURIComponent(path)}`,
    }),
    getRemote: builder.query<RemoteResp, void>({
      query: () => `${BASE}/remote`,
      providesTags: ["remote"],
    }),
    getCredentials: builder.query<{ credentials: CredentialOption[] }, void>({
      query: () => `${BASE}/credentials`,
      providesTags: ["credentials"],
    }),
    saveRemote: builder.mutation<unknown, RemoteSecretReq & { branch: string }>(
      {
        query: (body) => ({ url: `${BASE}/remote`, method: "POST", body }),
        // Saving the remote writes (and the gateway commits) the config-remote/credential
        // resources, so refresh the history table too — not just the remote indicator.
        invalidatesTags: ["remote", "history"],
      }
    ),
    removeRemote: builder.mutation<unknown, void>({
      query: () => ({ url: `${BASE}/remote-remove`, method: "POST", body: {} }),
      invalidatesTags: ["remote", "history"],
    }),
    testRemote: builder.mutation<unknown, RemoteSecretReq>({
      query: (body) => ({ url: `${BASE}/remote-test`, method: "POST", body }),
    }),
    push: builder.mutation<unknown, void>({
      query: () => ({ url: `${BASE}/push`, method: "POST", body: {} }),
      // History too: a push flips commits from local to remote.
      invalidatesTags: ["remote", "history"],
    }),
    getSecretProviders: builder.query<SecretProvidersResp, void>({
      query: () => `${BASE}/secret-providers`,
      providesTags: ["secretProviders"],
    }),
    addCredential: builder.mutation<
      { id: number; type: string },
      AddCredentialReq
    >({
      query: (body) => ({ url: `${BASE}/credentials`, method: "POST", body }),
      invalidatesTags: ["credentials"],
    }),
    restore: builder.mutation<unknown, { hash: string }>({
      query: (body) => ({ url: `${BASE}/restore`, method: "POST", body }),
      invalidatesTags: ["status", "history"],
    }),
    init: builder.mutation<unknown, void>({
      query: () => ({ url: `${BASE}/init`, method: "POST", body: {} }),
      invalidatesTags: ["status", "history"],
    }),
    // Fetch the remote and bring config to its HEAD (pull latest, or re-attach + recover
    // after a gateway-backup restore that dropped .git but kept the remote record).
    updateFromRemote: builder.mutation<{ hash: string }, void>({
      query: () => ({
        url: `${BASE}/update-from-remote`,
        method: "POST",
        body: {},
      }),
      invalidatesTags: ["status", "history", "remote"],
    }),
    deinit: builder.mutation<unknown, void>({
      query: () => ({ url: `${BASE}/deinit`, method: "POST", body: {} }),
      invalidatesTags: ["status", "history", "remote"],
    }),
  }),
  overrideExisting: false,
});

export const {
  useGetStatusQuery,
  useGetHistoryQuery,
  useGetCommitFilesQuery,
  useLazyGetFileDiffQuery,
  useGetRemoteQuery,
  useGetCredentialsQuery,
  useGetSecretProvidersQuery,
  useSaveRemoteMutation,
  useRemoveRemoteMutation,
  useTestRemoteMutation,
  usePushMutation,
  useAddCredentialMutation,
  useRestoreMutation,
  useInitMutation,
  useDeinitMutation,
  useUpdateFromRemoteMutation,
} = gitConfigApi;
