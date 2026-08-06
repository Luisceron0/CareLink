// Cliente HTTP único de la SPA.
//
// El access token vive SOLO en memoria (una variable de este módulo), nunca en
// localStorage ni sessionStorage: cualquier XSS en la página puede leer el storage,
// pero no puede leer una closure. El refresh token va en una cookie HttpOnly que el
// backend setea y que este código no puede tocar ni por accidente — que es el punto de
// que sea HttpOnly. El costo es que un F5 pierde la sesión hasta que /refresh la
// reconstruye desde la cookie, y eso es exactamente el trade-off que se quiere.
let accessToken = null;

export function setAccessToken(token) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export function clearAccessToken() {
  accessToken = null;
}

/** Decodifica el payload del JWT para leer rol/tenant. NO valida la firma — eso lo
 *  hace el backend en cada request. Acá solo sirve para decidir qué vistas mostrar;
 *  un usuario que altere su token en el cliente cambiará lo que ve, no lo que puede. */
export function decodeToken(token) {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

async function request(method, path, body, params) {
  const url = new URL(path, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
    });
  }

  const headers = { 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const res = await fetch(url, {
    method,
    headers,
    credentials: 'include', // la cookie HttpOnly del refresh token
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await res.text();
  const data = text ? safeJson(text) : null;

  if (!res.ok) {
    const error = new Error((data && data.error) || `HTTP ${res.status}`);
    error.status = res.status;
    error.body = data;
    throw error;
  }
  return data;
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

export const api = {
  get: (path, params) => request('GET', path, undefined, params),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
};
