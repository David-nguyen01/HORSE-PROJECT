import http from 'node:http'

const path = '/api/wallet/payos/webhook'
const server = http.createServer((request, response) => {
  if (request.method !== 'POST' || request.url !== path) {
    response.writeHead(404).end()
    request.resume()
    return
  }

  let bytes = 0
  const chunks = []
  request.on('data', chunk => {
    bytes += chunk.length
    if (bytes > 65536) {
      response.writeHead(413).end()
      request.destroy()
      return
    }
    chunks.push(chunk)
  })
  request.on('end', () => {
    if (response.writableEnded) return
    const upstream = http.request({ hostname: '127.0.0.1', port: 8080, path,
      method: 'POST', timeout: 10000,
      headers: { 'content-type': 'application/json', 'content-length': bytes } }, upstreamResponse => {
      response.writeHead(upstreamResponse.statusCode || 502, { 'content-type': 'application/json' })
      upstreamResponse.pipe(response)
    })
    upstream.on('error', () => { if (!response.writableEnded) response.writeHead(502).end() })
    upstream.end(Buffer.concat(chunks))
  })
})

server.listen(8099, '127.0.0.1', () => console.log('PayOS webhook-only proxy listening on 127.0.0.1:8099'))
