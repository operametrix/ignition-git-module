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
  defaultBranch: string;
  uri?: string;
  branch?: string;
  sshKeyId?: number;
  httpsCredentialId?: number;
  credentialLabel?: string;
  lastPush?: { time: number; ok: boolean; error?: string };
}

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
    saveRemote: builder.mutation<
      unknown,
      {
        uri: string;
        branch: string;
        sshKeyId: number;
        httpsCredentialId: number;
      }
    >({
      query: (body) => ({ url: `${BASE}/remote`, method: "POST", body }),
      invalidatesTags: ["remote"],
    }),
    removeRemote: builder.mutation<unknown, void>({
      query: () => ({ url: `${BASE}/remote-remove`, method: "POST", body: {} }),
      invalidatesTags: ["remote"],
    }),
    testRemote: builder.mutation<
      unknown,
      { uri: string; sshKeyId: number; httpsCredentialId: number }
    >({
      query: (body) => ({ url: `${BASE}/remote-test`, method: "POST", body }),
    }),
    push: builder.mutation<unknown, void>({
      query: () => ({ url: `${BASE}/push`, method: "POST", body: {} }),
      invalidatesTags: ["remote"],
    }),
    addCredential: builder.mutation<
      { id: number; type: string },
      | { type: "SSH"; name: string; key: string }
      | { type: "HTTPS"; host: string; username: string; password: string }
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
  useSaveRemoteMutation,
  useRemoveRemoteMutation,
  useTestRemoteMutation,
  usePushMutation,
  useAddCredentialMutation,
  useRestoreMutation,
  useInitMutation,
} = gitConfigApi;
