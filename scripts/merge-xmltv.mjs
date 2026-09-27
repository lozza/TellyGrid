import { readFile, writeFile } from 'node:fs/promises'

const [outputPath, ...inputPaths] = process.argv.slice(2)
if (!outputPath || inputPaths.length === 0) {
  throw new Error('Usage: node scripts/merge-xmltv.mjs <output.xml> <input.xml> [...]')
}

function xmlTvBody(xml, source) {
  const opening = xml.match(/<tv(?:\s[^>]*)?>/i)
  const closing = xml.match(/<\/tv>\s*$/i)
  if (!opening || !closing || closing.index == null) {
    throw new Error(`${source} is not an XMLTV document`)
  }
  return {
    opening: opening[0],
    body: xml.slice((opening.index ?? 0) + opening[0].length, closing.index),
  }
}

const documents = await Promise.all(inputPaths.map(async path => xmlTvBody(await readFile(path, 'utf8'), path)))
const merged = `<?xml version="1.0" encoding="UTF-8"?>\n${documents[0].opening}\n${documents.map(document => document.body.trim()).join('\n')}\n</tv>\n`
await writeFile(outputPath, merged, 'utf8')
