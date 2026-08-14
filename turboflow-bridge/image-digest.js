/**
 * 源图指纹。必须与服务端 DigestUtil.sha256Hex(imageBytes) 算出同一个值 ——
 * 服务端 completeTask 会拿 imageHash 和 subTask.imageHash 比对，不一致直接拒收，
 * 这是译图复用防止「把 A 图的译图配给 B 图」的安全网。
 */
export function stripDataUrl(value) {
  if (!value) return '';
  const comma = value.indexOf(',');
  return comma >= 0 ? value.substring(comma + 1) : value;
}

export async function imageDigest(imageBase64) {
  const binary = atob(stripDataUrl(imageBase64));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}
