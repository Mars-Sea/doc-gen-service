# Add image insertion to Excel generation

## Goal

Add image insertion support for Excel output so callers can include images in generated `.xlsx` documents, aligning Excel capabilities with the existing Word URL image payload behavior where practical.

## What I already know

* User wants Excel generation to support image insertion.
* Current Excel generation lives in `src/main/java/io/github/marssea/docgen/service/ExcelService.java`.
* Current Excel APIs are:
  * `POST /api/v1/doc/excel` using dynamic `headers` + `data` rows.
  * `POST /api/v1/doc/excel/fill` using an uploaded `.xlsx` template with `{variable}` and `{.field}` placeholders.
* `ExcelFillRequest.data` is `Map<String, Object>`, which can carry structured image payload maps without changing the request DTO.
* `ExcelGenRequest.data` is `List<List<Object>>`, which can also carry structured image payload maps in cells, but placement/row sizing is less template-controlled.
* Existing Word image payload format is detected by `ImagePayloadConverter`: `{"type":"image","url":"...","format":"png|jpg|jpeg","width":120,"height":60}`. `format`, `width`, and `height` have defaults/validation behavior in the converter.
* EasyExcel 4.0.1 has image support via `WriteCellData<byte[]>` and `ImageData`; built-in converters support `URL`, `byte[]`, `File`, and `InputStream` as image values.
* Current `ImagePayloadConverter` returns poi-tl `PictureRenderData`, so Excel image insertion likely needs either a shared downloader/validator or an Excel-specific converter.

## Assumptions (temporary)

* Reuse the same structured payload shape as Word images unless the user prefers a different Excel-specific API.
* Images should be inserted from HTTP/HTTPS URLs only, matching current Word security boundaries.
* PNG/JPG/JPEG support is enough for MVP, matching Word image support.
* Existing text/list Excel behavior must remain compatible.

## Open Questions

* None.

## Requirements (evolving)

* Excel template fill (`POST /api/v1/doc/excel/fill`) can include images from structured URL payloads in `data`.
* Reuse the existing Word image payload shape for Excel template images: `{"type":"image","url":"...","format":"png|jpg|jpeg","width":120,"height":60}`.
* Inserted image size follows payload `width`/`height` defaults and validation; MVP does not auto-adjust row height or column width.
* MVP does not add image support to dynamic Excel generation (`POST /api/v1/doc/excel`).
* Excel template image position is controlled by the placeholder cell in the uploaded `.xlsx` template.
* Invalid image payloads should use existing `InvalidImagePayloadException` semantics where possible.
* Existing Excel generation and template filling without images must keep working.

## Acceptance Criteria (evolving)

* [ ] Excel template fill can replace a `{logo}` placeholder with an actual image from `data.logo` structured image payload.
* [ ] Image payload validation accepts the same URL/format/width/height behavior as Word image payloads.
* [ ] Inserted images use payload/default dimensions without auto-adjusting the template row height or column width.
* [ ] Dynamic Excel generation (`/excel`) behavior is unchanged.
* [ ] Invalid image URL/protocol/format/size is rejected consistently with Word image payload validation.
* [ ] Existing Excel tests continue to pass.
* [ ] New regression tests verify the workbook contains an inserted picture.
* [ ] Formatting and Maven tests pass or any inability to run them is reported.

## Definition of Done (team quality bar)

* Tests added/updated for image insertion behavior.
* Spotless formatting applied/checkable.
* Relevant test suite run.
* Public API docs/examples updated if request payload behavior changes.

## Out of Scope (explicit)

* Non-HTTP image sources unless explicitly chosen.
* Additional image formats beyond png/jpg/jpeg unless explicitly chosen.
* Complex image positioning beyond MVP placement behavior.

## Research References

* Context7 EasyExcel docs (`/alibaba/easyexcel`) — template fill supports Map/list filling; image-specific docs are sparse.
* Local EasyExcel 4.0.1 sources — image writing uses `WriteCellData` + `ImageData`; built-in converters exist for URL/byte[]/File/InputStream images.

## Technical Notes

* Inspected `ExcelService.java`, `ExcelGenRequest.java`, `ExcelFillRequest.java`, `DocController.java`, `ExcelServiceTest.java`, `ImagePayloadConverter.java`, and README Excel sections.
* Likely impacted files: `ExcelService`, tests, maybe image conversion utility, and README/OpenAPI docs if public payload examples change.
* Backend specs require Spotless, JUnit 5, `@TempDir`, no path traversal bypass, and Chinese Javadocs for public APIs.
