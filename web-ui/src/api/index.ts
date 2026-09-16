import {
  BaseQueryFn,
  FetchArgs,
  FetchBaseQueryError,
  createApi,
  fetchBaseQuery,
} from "@reduxjs/toolkit/query/react";
import { BASE } from "../config";

// The gateway's web-session access control requires an X-CSRF-Token header on unsafe (non-GET)
// requests. We lazily fetch the current session's token from /csrf, cache it, and attach it by HTTP
// method rather than by RTK endpoint type, so a query issued as POST carries it too.
let csrfToken: string | null = null;

async function getCsrfToken(): Promise<string | null> {
  if (csrfToken) {
    return csrfToken;
  }
  try {
    const res = await fetch(`${BASE}/csrf`, {
      headers: { Accept: "application/json" },
      credentials: "same-origin",
    });
    if (res.ok) {
      const json = await res.json();
      csrfToken = json && json.token ? json.token : null;
    }
  } catch {
    // leave token null; the request will surface any auth error
  }
  return csrfToken;
}

const rawBaseQuery = fetchBaseQuery({
  baseUrl: "",
  prepareHeaders: (headers) => {
    headers.set("Accept", "application/json");
    return headers;
  },
});

const isUnsafe = (args: string | FetchArgs) =>
  typeof args !== "string" &&
  !!args.method &&
  args.method.toUpperCase() !== "GET";

const withCsrf = async (args: FetchArgs): Promise<FetchArgs> => {
  const token = await getCsrfToken();
  if (!token) {
    return args;
  }
  const headers = new Headers(args.headers as HeadersInit | undefined);
  headers.set("X-CSRF-Token", token);
  return { ...args, headers };
};

// A cached token goes stale when the session changes (timeout, re-login in another tab), and every
// mutation then failed until the page was reloaded. Drop the token and retry once with a fresh one
// before surfacing the error.
const baseQuery: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  if (!isUnsafe(args)) {
    return rawBaseQuery(args, api, extraOptions);
  }
  let result = await rawBaseQuery(
    await withCsrf(args as FetchArgs),
    api,
    extraOptions
  );
  const status = result.error ? result.error.status : null;
  if (status === 401 || status === 403) {
    csrfToken = null;
    result = await rawBaseQuery(
      await withCsrf(args as FetchArgs),
      api,
      extraOptions
    );
  }
  return result;
};

const baseApi = createApi({
  reducerPath: "gitConfigApi",
  baseQuery,
  tagTypes: ["status", "history", "remote", "credentials", "secretProviders"],
  endpoints: () => ({}),
  keepUnusedDataFor: 0,
});

export default baseApi;
