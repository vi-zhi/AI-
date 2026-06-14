/**
 * 将 draw.io XML 编码为 diagrams.net iframe URL 可用的格式
 */
export function encodeXmlForUrl(xml: string): string {
  const encoded = btoa(unescape(encodeURIComponent(xml)))
  return encoded.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

/**
 * 构建 draw.io iframe 的 src URL
 * 支持内嵌 XML 数据
 */
export function buildDrawioUrl(xml?: string): string {
  const base = 'https://embed.diagrams.net/?embed=1&ui=atlas&splash=0&noSaveAs=1&noToolbar=0&configure=1'

  if (xml) {
    const encoded = encodeXmlForUrl(xml)
    return `${base}&xml=${encoded}`
  }

  return base
}
