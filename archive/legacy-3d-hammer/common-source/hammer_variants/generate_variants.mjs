import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const commonDir = path.resolve(scriptDir, "..");
const workspaceDir = path.resolve(commonDir, "../..");
const vanillaDir = path.join(workspaceDir, "resources", "_needed", "1.21.11");
const vanillaAssetsDir = path.join(vanillaDir, "assets", "minecraft", "textures");
const vanillaTagsDir = path.join(vanillaDir, "data", "minecraft", "tags", "item");
const modelOutputDir = path.join(
  scriptDir,
  "assets",
  "common",
  "models",
  "item",
  "hammers",
);
const textureOutputDir = path.join(
  scriptDir,
  "assets",
  "common",
  "textures",
  "item",
  "hammers",
);

const masterModelPath = path.join(commonDir, "golden_hammer.json");
const masterTexturePath = path.join(commonDir, "golden_hammer_16x16.png");

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    encoding: options.encoding ?? null,
    input: options.input,
    maxBuffer: 32 * 1024 * 1024,
  });
  if (result.status !== 0) {
    const stderr = Buffer.isBuffer(result.stderr)
      ? result.stderr.toString("utf8")
      : result.stderr;
    throw new Error(`${command} failed:\n${stderr}`);
  }
  return result.stdout;
}

function decodePng(filePath) {
  const dimensions = run(
    "ffprobe",
    [
      "-v",
      "error",
      "-select_streams",
      "v:0",
      "-show_entries",
      "stream=width,height",
      "-of",
      "csv=p=0:s=x",
      filePath,
    ],
    { encoding: "utf8" },
  )
    .trim()
    .split("x")
    .map(Number);
  const [width, height] = dimensions;
  const pixels = run("ffmpeg", [
    "-hide_banner",
    "-loglevel",
    "error",
    "-i",
    filePath,
    "-f",
    "rawvideo",
    "-pix_fmt",
    "rgba",
    "pipe:1",
  ]);
  const expectedLength = width * height * 4;
  if (pixels.length !== expectedLength) {
    throw new Error(
      `Unexpected RGBA length for ${filePath}: ${pixels.length} != ${expectedLength}`,
    );
  }
  return { width, height, pixels: Buffer.from(pixels) };
}

function encodePng(filePath, width, height, pixels) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  run(
    "ffmpeg",
    [
      "-hide_banner",
      "-loglevel",
      "error",
      "-f",
      "rawvideo",
      "-pix_fmt",
      "rgba",
      "-s",
      `${width}x${height}`,
      "-i",
      "pipe:0",
      "-frames:v",
      "1",
      "-y",
      filePath,
    ],
    { input: pixels },
  );
}

function srgbChannelToLinear(value) {
  const normalized = value / 255;
  return normalized <= 0.04045
    ? normalized / 12.92
    : ((normalized + 0.055) / 1.055) ** 2.4;
}

function rgbToOklab([red, green, blue]) {
  const r = srgbChannelToLinear(red);
  const g = srgbChannelToLinear(green);
  const b = srgbChannelToLinear(blue);

  const l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
  const m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
  const s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

  const lRoot = Math.cbrt(l);
  const mRoot = Math.cbrt(m);
  const sRoot = Math.cbrt(s);

  return [
    0.2104542553 * lRoot + 0.793617785 * mRoot - 0.0040720468 * sRoot,
    1.9779984951 * lRoot - 2.428592205 * mRoot + 0.4505937099 * sRoot,
    0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.808675766 * sRoot,
  ];
}

function colorDistance(left, right) {
  const lightness = (left[0] - right[0]) * 1.25;
  const greenRed = left[1] - right[1];
  const blueYellow = left[2] - right[2];
  return (
    lightness * lightness +
    greenRed * greenRed +
    blueYellow * blueYellow
  );
}

function rgbKey([red, green, blue]) {
  return `${red},${green},${blue}`;
}

function rgbToHex([red, green, blue]) {
  return `#${[red, green, blue]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("")}`;
}

function hexToRgb(hex) {
  const match = hex.match(/^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
  if (!match) {
    throw new Error(`Invalid RGB hex color: ${hex}`);
  }
  return match.slice(1).map((channel) => Number.parseInt(channel, 16));
}

function collectOpaqueColors(image) {
  const colors = [];
  for (let offset = 0; offset < image.pixels.length; offset += 4) {
    if (image.pixels[offset + 3] === 0) {
      continue;
    }
    colors.push([
      image.pixels[offset],
      image.pixels[offset + 1],
      image.pixels[offset + 2],
    ]);
  }
  return colors;
}

function nearestCenterIndex(lab, centers) {
  let bestIndex = 0;
  let bestDistance = Number.POSITIVE_INFINITY;
  for (let index = 0; index < centers.length; index += 1) {
    const distance = colorDistance(lab, centers[index]);
    if (distance < bestDistance) {
      bestDistance = distance;
      bestIndex = index;
    }
  }
  return bestIndex;
}

function quantizeToSeven(colors) {
  const counts = new Map();
  for (const color of colors) {
    const key = rgbKey(color);
    const existing = counts.get(key);
    if (existing) {
      existing.count += 1;
    } else {
      counts.set(key, {
        rgb: color,
        lab: rgbToOklab(color),
        count: 1,
      });
    }
  }
  const entries = [...counts.values()].sort(
    (left, right) => left.lab[0] - right.lab[0],
  );
  while (entries.length < 7) {
    let widestGapIndex = 0;
    let widestGap = -1;
    for (let index = 0; index < entries.length - 1; index += 1) {
      const gap = entries[index + 1].lab[0] - entries[index].lab[0];
      if (gap > widestGap) {
        widestGap = gap;
        widestGapIndex = index;
      }
    }
    const darker = entries[widestGapIndex].rgb;
    const lighter = entries[widestGapIndex + 1].rgb;
    const interpolated = darker.map((channel, index) =>
      Math.round((channel + lighter[index]) / 2),
    );
    entries.push({
      rgb: interpolated,
      lab: rgbToOklab(interpolated),
      count: 1,
    });
    entries.sort((left, right) => left.lab[0] - right.lab[0]);
  }

  const totalWeight = entries.reduce((sum, entry) => sum + entry.count, 0);
  const centers = [];
  for (let index = 0; index < 7; index += 1) {
    const targetWeight = ((index + 0.5) / 7) * totalWeight;
    let runningWeight = 0;
    let selected = entries.at(-1);
    for (const entry of entries) {
      runningWeight += entry.count;
      if (runningWeight >= targetWeight) {
        selected = entry;
        break;
      }
    }
    if (centers.some((center) => colorDistance(center, selected.lab) < 1e-12)) {
      selected = entries
        .map((entry) => ({
          entry,
          distance: Math.min(
            ...centers.map((center) => colorDistance(entry.lab, center)),
          ),
        }))
        .sort((left, right) => right.distance - left.distance)[0].entry;
    }
    centers.push([...selected.lab]);
  }

  let assignments = new Array(entries.length).fill(0);
  for (let iteration = 0; iteration < 64; iteration += 1) {
    assignments = entries.map((entry) =>
      nearestCenterIndex(entry.lab, centers),
    );
    const nextCenters = centers.map(() => [0, 0, 0, 0]);
    for (let index = 0; index < entries.length; index += 1) {
      const entry = entries[index];
      const accumulator = nextCenters[assignments[index]];
      accumulator[0] += entry.lab[0] * entry.count;
      accumulator[1] += entry.lab[1] * entry.count;
      accumulator[2] += entry.lab[2] * entry.count;
      accumulator[3] += entry.count;
    }
    for (let index = 0; index < nextCenters.length; index += 1) {
      const accumulator = nextCenters[index];
      if (accumulator[3] === 0) {
        const replacement = entries
          .map((entry) => ({
            entry,
            distance: Math.min(
              ...centers.map((center) => colorDistance(entry.lab, center)),
            ),
          }))
          .sort((left, right) => right.distance - left.distance)[0].entry;
        nextCenters[index] = [...replacement.lab, 1];
      } else {
        nextCenters[index] = [
          accumulator[0] / accumulator[3],
          accumulator[1] / accumulator[3],
          accumulator[2] / accumulator[3],
          accumulator[3],
        ];
      }
    }
    const movement = centers.reduce(
      (sum, center, index) =>
        sum + colorDistance(center, nextCenters[index]),
      0,
    );
    for (let index = 0; index < centers.length; index += 1) {
      centers[index] = nextCenters[index].slice(0, 3);
    }
    if (movement < 1e-12) {
      break;
    }
  }

  assignments = entries.map((entry) => nearestCenterIndex(entry.lab, centers));
  const medoids = centers.map((center, centerIndex) => {
    const clusterEntries = entries.filter(
      (_, entryIndex) => assignments[entryIndex] === centerIndex,
    );
    const candidates = clusterEntries.length > 0 ? clusterEntries : entries;
    return candidates
      .map((entry) => ({
        entry,
        distance: colorDistance(entry.lab, center),
      }))
      .sort(
        (left, right) =>
          left.distance - right.distance ||
          right.entry.count - left.entry.count,
      )[0].entry;
  });

  const orderedClusters = centers
    .map((center, index) => ({ center, medoid: medoids[index], index }))
    .sort((left, right) => left.center[0] - right.center[0]);
  const rankByCluster = new Map(
    orderedClusters.map((cluster, rank) => [cluster.index, rank]),
  );

  return {
    centers,
    palette: orderedClusters.map((cluster) => cluster.medoid.rgb),
    rankForColor(color) {
      const clusterIndex = nearestCenterIndex(rgbToOklab(color), centers);
      return rankByCluster.get(clusterIndex);
    },
  };
}

function readTag(tagName) {
  const tag = JSON.parse(
    fs.readFileSync(path.join(vanillaTagsDir, `${tagName}.json`), "utf8"),
  );
  return tag.values;
}

function minecraftPath(identifier) {
  return identifier.replace(/^minecraft:/, "");
}

const woodenToolNames = ["axe", "hoe", "pickaxe", "shovel", "sword"];
const woodMaterials = [
  {
    id: "wooden",
    category: "wood",
    sourceTextures: woodenToolNames.map((toolName) =>
      path.join(vanillaAssetsDir, "item", `wooden_${toolName}.png`),
    ),
  },
];

const stoneMaterials = readTag("stone_tool_materials").map((identifier) => {
  const id = minecraftPath(identifier);
  return {
    id,
    category: "stone",
    sourceTextures: [path.join(vanillaAssetsDir, "block", `${id}.png`)],
  };
});

const refinedMaterials = [
  {
    id: "copper",
    category: "metal",
    sourceTextures: [
      path.join(vanillaAssetsDir, "item", "copper_ingot.png"),
    ],
  },
  {
    id: "iron",
    category: "metal",
    sourceTextures: [path.join(vanillaAssetsDir, "item", "iron_ingot.png")],
  },
  {
    id: "gold",
    category: "metal",
    sourceTextures: [path.join(vanillaAssetsDir, "item", "gold_ingot.png")],
  },
  {
    id: "diamond",
    category: "gem",
    sourceTextures: [path.join(vanillaAssetsDir, "item", "diamond.png")],
  },
  {
    id: "netherite",
    category: "metal",
    sourceTextures: [
      path.join(vanillaAssetsDir, "item", "netherite_ingot.png"),
    ],
  },
];

// Vanilla textures guide the material hue and character, but these ramps are
// deliberately authored rather than copied. Adjacent shades use restrained
// contrast like the user's original golden-hammer palette.
const designedPaletteHex = {
  wooden: [
    "#32270f",
    "#3e3012",
    "#4b3915",
    "#594419",
    "#69511e",
    "#7a5f24",
    "#8d6e2c",
  ],
  cobblestone: [
    "#5f6260",
    "#6b6e6b",
    "#777a76",
    "#848783",
    "#91958f",
    "#a0a49d",
    "#b0b4ac",
  ],
  blackstone: [
    "#29252c",
    "#312d35",
    "#3a3640",
    "#443f4a",
    "#4f4a55",
    "#5b5661",
    "#68636e",
  ],
  cobbled_deepslate: [
    "#353b3e",
    "#3e4548",
    "#485053",
    "#535b5e",
    "#5f676a",
    "#6c7477",
    "#7a8285",
  ],
  copper: [
    "#9d5139",
    "#ac5b40",
    "#bc6649",
    "#cd7253",
    "#dc805f",
    "#e99070",
    "#f3a486",
  ],
  iron: [
    "#747b7b",
    "#818888",
    "#8e9695",
    "#9da4a2",
    "#adb3b0",
    "#bec4c0",
    "#d1d6d1",
  ],
  gold: [
    "#d49515",
    "#ebae12",
    "#f9bd23",
    "#ffd83e",
    "#ffec4f",
    "#fffd90",
    "#fdfcca",
  ],
  diamond: [
    "#168f89",
    "#1b9f98",
    "#24afa6",
    "#31c0b4",
    "#43d0c2",
    "#5ce0d1",
    "#81eee0",
  ],
  netherite: [
    "#31282d",
    "#393035",
    "#42393e",
    "#4c4348",
    "#574e53",
    "#635a5f",
    "#71686d",
  ],
};

const materials = [...woodMaterials, ...stoneMaterials, ...refinedMaterials];
if (materials.length !== 9) {
  throw new Error(`Expected 9 vanilla tool materials, found ${materials.length}`);
}

for (const material of materials) {
  const sourceColors = [];
  for (const sourceTexture of material.sourceTextures) {
    if (!fs.existsSync(sourceTexture)) {
      throw new Error(`Missing palette source: ${sourceTexture}`);
    }
    sourceColors.push(...collectOpaqueColors(decodePng(sourceTexture)));
  }
  if (!designedPaletteHex[material.id]) {
    throw new Error(`Missing designed palette for ${material.id}`);
  }
  material.referenceColorKeys = new Set(sourceColors.map(rgbKey));
  material.palette = designedPaletteHex[material.id].map(hexToRgb);
}

const masterTexture = decodePng(masterTexturePath);
if (masterTexture.width !== 16 || masterTexture.height !== 16) {
  throw new Error("The master hammer texture must be 16x16");
}

const headPixelOffsets = [];
const headColors = [];
for (let y = 0; y < 16; y += 1) {
  for (let x = 0; x < 16; x += 1) {
    const isHeadQuadrant = y < 8 || (x < 8 && y >= 8);
    const offset = (y * 16 + x) * 4;
    if (isHeadQuadrant && masterTexture.pixels[offset + 3] > 0) {
      headPixelOffsets.push(offset);
      headColors.push([
        masterTexture.pixels[offset],
        masterTexture.pixels[offset + 1],
        masterTexture.pixels[offset + 2],
      ]);
    }
  }
}
const masterHeadQuantizer = quantizeToSeven(headColors);
if (
  JSON.stringify(masterHeadQuantizer.palette.map(rgbToHex)) !==
  JSON.stringify(designedPaletteHex.gold)
) {
  throw new Error("The designed gold palette must preserve the master texture");
}

fs.mkdirSync(modelOutputDir, { recursive: true });
fs.mkdirSync(textureOutputDir, { recursive: true });

const materialIds = new Set(materials.map((material) => material.id));
function removeStaleVariants(directory, extension) {
  for (const fileName of fs.readdirSync(directory)) {
    const match = fileName.match(
      new RegExp(`^(.+)_hammer\\.${extension.replace(".", "\\.")}$`),
    );
    if (match && !materialIds.has(match[1])) {
      fs.unlinkSync(path.join(directory, fileName));
    }
  }
}
removeStaleVariants(modelOutputDir, "json");
removeStaleVariants(textureOutputDir, "png");

const masterModel = JSON.parse(fs.readFileSync(masterModelPath, "utf8"));

for (const material of materials) {
  const recoloredPixels = Buffer.from(masterTexture.pixels);
  for (const offset of headPixelOffsets) {
    const sourceColor = [
      masterTexture.pixels[offset],
      masterTexture.pixels[offset + 1],
      masterTexture.pixels[offset + 2],
    ];
    const rank = masterHeadQuantizer.rankForColor(sourceColor);
    const targetColor = material.palette[rank];
    recoloredPixels[offset] = targetColor[0];
    recoloredPixels[offset + 1] = targetColor[1];
    recoloredPixels[offset + 2] = targetColor[2];
  }
  encodePng(
    path.join(textureOutputDir, `${material.id}_hammer.png`),
    16,
    16,
    recoloredPixels,
  );

  const model = JSON.parse(JSON.stringify(masterModel));
  const textureReference = `common:item/hammers/${material.id}_hammer`;
  model.textures = {
    0: textureReference,
    particle: textureReference,
  };
  fs.writeFileSync(
    path.join(modelOutputDir, `${material.id}_hammer.json`),
    `${JSON.stringify(model, null, "\t")}\n`,
  );
}

const swatchSize = 8;
const paletteSheetWidth = 7 * swatchSize;
const paletteSheetHeight = materials.length * swatchSize;
const paletteSheetPixels = Buffer.alloc(
  paletteSheetWidth * paletteSheetHeight * 4,
);
for (let row = 0; row < materials.length; row += 1) {
  for (let column = 0; column < 7; column += 1) {
    const color = materials[row].palette[column];
    for (let y = row * swatchSize; y < (row + 1) * swatchSize; y += 1) {
      for (
        let x = column * swatchSize;
        x < (column + 1) * swatchSize;
        x += 1
      ) {
        const offset = (y * paletteSheetWidth + x) * 4;
        paletteSheetPixels[offset] = color[0];
        paletteSheetPixels[offset + 1] = color[1];
        paletteSheetPixels[offset + 2] = color[2];
        paletteSheetPixels[offset + 3] = 255;
      }
    }
  }
}
encodePng(
  path.join(scriptDir, "palettes.png"),
  paletteSheetWidth,
  paletteSheetHeight,
  paletteSheetPixels,
);

const paletteManifest = {
  source_version: "Minecraft Java Edition 1.21.11",
  palette_method:
    "Hand-designed seven-shade ramps informed by vanilla textures and matched to the subtle contrast rhythm of the user's golden-hammer palette; colors are not copied verbatim from the reference textures",
  shade_order: "darkest_to_brightest",
  colors_per_material: 7,
  materials: materials.map((material) => ({
    id: material.id,
    category: material.category,
    source_textures: material.sourceTextures.map((sourceTexture) =>
      path.relative(workspaceDir, sourceTexture),
    ),
    colors: material.palette.map(rgbToHex),
  })),
};
fs.writeFileSync(
  path.join(scriptDir, "palettes.json"),
  `${JSON.stringify(paletteManifest, null, 2)}\n`,
);

const readmeLines = [
  "# Vanilla Material Hammer Variants",
  "",
  "Generated from the saved `resources/common/golden_hammer.json` model.",
  "Every model preserves that master model's geometry, UVs, and display transforms.",
  "",
  "The material list represents Minecraft Java Edition 1.21.11 vanilla tool",
  "tiers: one wooden palette referencing all five wooden tool textures, all",
  "stone-tool ingredients, and copper, iron, gold, diamond, and netherite.",
  "",
  "Each head uses a hand-designed seven-color, darkest-to-brightest palette.",
  "Vanilla textures guide the material hue and character, but their colors are",
  "not copied. The restrained contrast follows the original golden hammer,",
  "whose exact seven-color head palette is preserved. The original wooden",
  "handle pixels are unchanged for visual consistency between variants.",
  "",
  "Files:",
  "",
  "- `palettes.json`: named seven-color palettes and vanilla source textures",
  "- `palettes.png`: palette rows in the same order as `palettes.json`",
  "- `assets/common/textures/item/hammers/`: 9 recolored 16x16 textures",
  "- `assets/common/models/item/hammers/`: 9 Minecraft Java item models",
  "",
  "Materials:",
  "",
  ...materials.map(
    (material) =>
      `- \`${material.id}\` (${material.category}): ${material.palette
        .map(rgbToHex)
        .join(", ")}`,
  ),
  "",
];
fs.writeFileSync(path.join(scriptDir, "README.md"), readmeLines.join("\n"));

const masterWithoutTextures = JSON.parse(JSON.stringify(masterModel));
delete masterWithoutTextures.textures;
for (const material of materials) {
  const expectedPalette = new Set(material.palette.map(rgbKey));
  if (expectedPalette.size !== 7) {
    throw new Error(`${material.id} palette does not contain seven unique colors`);
  }
  for (let index = 1; index < material.palette.length; index += 1) {
    const previousLightness = rgbToOklab(material.palette[index - 1])[0];
    const currentLightness = rgbToOklab(material.palette[index])[0];
    if (currentLightness <= previousLightness) {
      throw new Error(`${material.id} palette is not ordered by lightness`);
    }
  }
  const copiedReferenceColors = material.palette.filter((color) =>
    material.referenceColorKeys.has(rgbKey(color)),
  );
  if (copiedReferenceColors.length > 0) {
    throw new Error(
      `${material.id} palette copies ${copiedReferenceColors.length} vanilla RGB values`,
    );
  }

  const texturePath = path.join(
    textureOutputDir,
    `${material.id}_hammer.png`,
  );
  const generatedTexture = decodePng(texturePath);
  if (generatedTexture.width !== 16 || generatedTexture.height !== 16) {
    throw new Error(`${material.id} hammer texture is not 16x16`);
  }
  const generatedHeadColors = new Set();
  for (let y = 0; y < 16; y += 1) {
    for (let x = 0; x < 16; x += 1) {
      const offset = (y * 16 + x) * 4;
      const isHeadQuadrant = y < 8 || (x < 8 && y >= 8);
      if (
        generatedTexture.pixels[offset + 3] !==
        masterTexture.pixels[offset + 3]
      ) {
        throw new Error(`${material.id} changed the master alpha layout`);
      }
      if (isHeadQuadrant && generatedTexture.pixels[offset + 3] > 0) {
        generatedHeadColors.add(
          rgbKey([
            generatedTexture.pixels[offset],
            generatedTexture.pixels[offset + 1],
            generatedTexture.pixels[offset + 2],
          ]),
        );
      } else {
        for (let channel = 0; channel < 4; channel += 1) {
          if (
            generatedTexture.pixels[offset + channel] !==
            masterTexture.pixels[offset + channel]
          ) {
            throw new Error(`${material.id} changed a handle/background pixel`);
          }
        }
      }
    }
  }
  if (
    generatedHeadColors.size !== 7 ||
    [...generatedHeadColors].some((color) => !expectedPalette.has(color))
  ) {
    throw new Error(
      `${material.id} hammer head does not use its complete seven-color palette`,
    );
  }

  const modelPath = path.join(modelOutputDir, `${material.id}_hammer.json`);
  const generatedModel = JSON.parse(fs.readFileSync(modelPath, "utf8"));
  const expectedTextureReference = `common:item/hammers/${material.id}_hammer`;
  if (
    generatedModel.textures?.["0"] !== expectedTextureReference ||
    generatedModel.textures?.particle !== expectedTextureReference
  ) {
    throw new Error(`${material.id} model has an incorrect texture reference`);
  }
  delete generatedModel.textures;
  if (
    JSON.stringify(generatedModel) !== JSON.stringify(masterWithoutTextures)
  ) {
    throw new Error(`${material.id} model diverged from the saved master model`);
  }
}

process.stdout.write(
  `Generated and validated ${materials.length} palettes, textures, and models in ${scriptDir}\n`,
);
