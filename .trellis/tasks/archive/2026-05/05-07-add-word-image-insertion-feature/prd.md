# Add Word image insertion feature

## Goal

Add support for rendering images into generated Word documents so callers can provide image data in a request and place those images in `.docx` templates.

## What I already know

* The user wants a new Word feature that can insert images.
* This service uses Spring Boot 3.2.1, Java 17, Maven, and poi-tl 1.12.1 for Word generation.
* Current Word generation is template-based through `WordService.generateWord(...)` and `WordService.generateBatch(...)`.
* Current request payloads pass arbitrary `Map<String, Object>` data through `WordGenRequest.data` and `WordBatchRequest.dataList`.
* Current Word template rendering auto-binds iterable fields to `LoopRowTableRenderPolicy` for table row loops.
* poi-tl image placeholders use `{{@imageKey}}` and require image values to be converted to `PictureRenderData`.
* poi-tl supports image data from local paths, URLs, byte arrays, streams, and Base64 strings via `Pictures`.

## Assumptions (temporary)

* The MVP should preserve the existing `/api/v1/doc/word` and `/api/v1/doc/word/batch` APIs rather than adding a new endpoint.
* Image insertion should work by detecting structured image objects inside the existing data maps and converting them before rendering.
* The MVP will support URL image input because the user selected URL images as the initial source format.
* URL image input will not restrict domains in the MVP; it should still enforce basic URL validation, protocol constraints, timeouts, and supported image formats.

## Open Questions

* Batch Word generation should support URL image payloads in each `dataList` item.

## Requirements (evolving)

* Render Word templates containing poi-tl image placeholders such as `{{@logo}}`.
* Support URL-based image insertion in single Word generation.
* Support URL-based image insertion in batch Word generation for each `dataList` item.
* Image payloads are structured objects in the existing render data maps, with required URL and optional image format, width, and height.
* URL image input supports unrestricted domains in the MVP.
* URL image input validates protocol, applies network timeouts, and accepts only supported image formats.
* Image format can be omitted and inferred from URL suffix or response Content-Type.
* Image width and height can be omitted and default to 300 x 200.

## Acceptance Criteria (evolving)

* [ ] A caller can generate a `.docx` where an image placeholder is replaced by a URL image.
* [ ] A caller can batch-generate a `.docx` where each record can include URL images.
* [ ] Existing text and table-loop rendering behavior still works.
* [ ] Invalid image payloads fail with a clear client-facing error.
* [ ] Unit/controller tests cover successful image insertion and failure behavior.

## Definition of Done (team quality bar)

* Tests added/updated where appropriate.
* Maven tests pass.
* Spotless formatting passes or is applied.
* README/API examples updated if request behavior changes.

## Out of Scope (explicit)

* Editing existing `.docx` files without templates.
* Rich image transformations beyond basic width/height sizing.
* Remote image fetching unless selected for MVP.
* Image upload/storage management.

## Research References

* Context7 `/sayi/poi-tl` docs — poi-tl supports `{{@var}}` image tags and `Pictures.ofBase64`, `Pictures.ofBytes`, `Pictures.ofLocal`, and `Pictures.ofUrl` to create `PictureRenderData`.

## Technical Notes

* Likely impacted files:
  * `src/main/java/io/github/marssea/docgen/service/WordService.java`
  * `src/main/java/io/github/marssea/docgen/model/WordGenRequest.java`
  * `src/main/java/io/github/marssea/docgen/model/WordBatchRequest.java`
  * `src/test/java/io/github/marssea/docgen/service/WordServiceTest.java`
  * `src/test/java/io/github/marssea/docgen/controller/DocControllerTest.java`
  * `README.md` / `README_CN.md` if API examples change
* Current `buildRenderConfig` can be extended to detect image payloads and bind/convert them before rendering.
* Template syntax should be documented as `{{@fieldName}}` for images, distinct from `{{fieldName}}` text placeholders.
