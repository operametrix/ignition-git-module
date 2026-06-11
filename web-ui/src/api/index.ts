import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import { BASE } from "../config";

// The gateway's web-session access control requires an X-CSRF-Token header on unsafe (mutating)
// requests. GET is exempt, so we lazily fetch the current session's token from /csrf and cache it,
// then attach it on mutations.
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
    // leave token null; the mutation will surface any auth error
  }
  return csrfToken;
}

const baseQuery = fetchBaseQuery({
  baseUrl: "",
  prepareHeaders: async (headers, { type }) => {
    headers.set("Accept", "application/json");
    if (type === "mutation") {
      const token = await getCsrfToken();
      if (token) {
        headers.set("X-CSRF-Token", token);
      }
    }
    return headers;
  },
});

const baseApi = createApi({
  reducerPath: "gitConfigApi",
  baseQuery,
  tagTypes: ["status", "history"],
  endpoints: () => ({}),
  keepUnusedDataFor: 0,
});

export default baseApi;
