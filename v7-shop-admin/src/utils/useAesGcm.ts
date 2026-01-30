// useAesGcm.ts
// AES-256-GCM + PBKDF2(HMAC-SHA256)
// 输出格式：Base64( salt[16] | iv[12] | ciphertext+tag )

const textEncoder = new TextEncoder()
const textDecoder = new TextDecoder()

// 工具：ArrayBuffer <-> Base64
function ab2b64(ab: ArrayBuffer): string {
  const bytes = new Uint8Array(ab)
  let binary = ''
  // eslint-disable-next-line unicorn/prefer-code-point
  for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i])
  return btoa(binary)
}
function b642ab(b64: string): ArrayBuffer {
  const binary = atob(b64)
  const bytes = new Uint8Array(binary.length)
  // eslint-disable-next-line unicorn/prefer-code-point
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

// 口令 -> 密钥（PBKDF2 派生 AES-256）
async function deriveKey(password: string, salt: Uint8Array, iterations = 100_000) {
  const baseKey = await crypto.subtle.importKey(
    'raw',
    textEncoder.encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  )
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations,
      hash: 'SHA-256',
    },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  )
}

export function useAesGcm() {
  // 加密：明文字符串 -> Base64 封包
  const encrypt = async (plaintext: string, password: string): Promise<string> => {
    const salt = crypto.getRandomValues(new Uint8Array(16)) // 16B salt
    const iv = crypto.getRandomValues(new Uint8Array(12)) // 12B GCM IV
    const key = await deriveKey(password, salt)

    const ct = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv, tagLength: 128 },
      key,
      textEncoder.encode(plaintext)
    )

    // 拼接 salt | iv | ciphertext+tag
    const packet = new Uint8Array(salt.length + iv.length + ct.byteLength)
    packet.set(salt, 0)
    packet.set(iv, salt.length)
    packet.set(new Uint8Array(ct), salt.length + iv.length)

    return ab2b64(packet.buffer)
  }

  // 可选：前端自测解密（生产一般由后端解密）
  const decrypt = async (packetB64: string, password: string): Promise<string> => {
    const packet = new Uint8Array(b642ab(packetB64))
    const salt = packet.slice(0, 16)
    const iv = packet.slice(16, 28)
    const ct = packet.slice(28)
    const key = await deriveKey(password, salt)
    const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv, tagLength: 128 }, key, ct)
    return textDecoder.decode(pt)
  }

  return { encrypt, decrypt }
}
