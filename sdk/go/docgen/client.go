// Package docgen 提供调用文档生成服务 API 的 Go 客户端
//
// 使用示例:
//
//	client := docgen.NewClient("http://localhost:8081")
//	data := map[string]any{
//	    "title": "My Report",
//	    "date":  "2025-01-01",
//	}
//	doc, err := client.GenerateWord("template.docx", data, "output_report")
//	if err != nil {
//	    log.Fatal(err)
//	}
//	os.WriteFile("report.docx", doc, 0644)
package docgen

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// Client 文档生成服务客户端
type Client struct {
	// BaseURL 服务地址，如 http://localhost:8081
	BaseURL string
	// HTTPClient HTTP 客户端，可自定义超时等配置
	HTTPClient *http.Client
}

// WordGenRequest Word 文档生成请求参数
type WordGenRequest struct {
	// TemplateName 模板文件名（需包含扩展名，如 template.docx）
	TemplateName string `json:"templateName"`
	// Data 模板渲染数据
	Data map[string]any `json:"data"`
	// FileName 自定义输出文件名（不含扩展名，可选）
	FileName string `json:"fileName,omitempty"`
}

// ExcelGenRequest Excel 生成请求参数
type ExcelGenRequest struct {
	// SheetName 工作表名称（可选，默认 "Sheet1"）
	SheetName string `json:"sheetName,omitempty"`
	// Headers 表头列名列表
	Headers []string `json:"headers"`
	// Data 数据行（二维数组）
	Data [][]any `json:"data"`
	// FileName 自定义输出文件名（不含扩展名，可选）
	FileName string `json:"fileName,omitempty"`
}

// ExcelFillRequest Excel 模板填充请求参数
type ExcelFillRequest struct {
	// TemplateName 模板文件名（需包含扩展名，如 template.xlsx）
	TemplateName string `json:"templateName"`
	// Data 单值变量数据（对应模板中的 {variable} 语法）
	Data map[string]any `json:"data,omitempty"`
	// ListData 列表数据（对应模板中的 {.field} 语法，用于行循环）
	ListData map[string][]map[string]any `json:"listData,omitempty"`
	// FileName 自定义输出文件名（不含扩展名，可选）
	FileName string `json:"fileName,omitempty"`
}

// WordBatchRequest 批量 Word 生成请求参数
type WordBatchRequest struct {
	// TemplateName 模板文件名（需包含扩展名，如 template.docx）
	TemplateName string `json:"templateName"`
	// DataList 数据列表，每条数据生成一页
	DataList []map[string]any `json:"dataList"`
	// FileName 自定义输出文件名（不含扩展名，可选）
	FileName string `json:"fileName,omitempty"`
}

// ErrorResponse 错误响应结构
type ErrorResponse struct {
	Status  int    `json:"status"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

// Error 实现 error 接口
func (e *ErrorResponse) Error() string {
	return fmt.Sprintf("[%s] %s (status: %d)", e.Code, e.Message, e.Status)
}

// NewClient 创建文档生成服务客户端
//
// baseURL: 服务地址，如 http://localhost:8081
func NewClient(baseURL string) *Client {
	return &Client{
		BaseURL: baseURL,
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// NewClientWithTimeout 创建带自定义超时的客户端
func NewClientWithTimeout(baseURL string, timeout time.Duration) *Client {
	return &Client{
		BaseURL: baseURL,
		HTTPClient: &http.Client{
			Timeout: timeout,
		},
	}
}

// HealthResponse 健康检查响应
type HealthResponse struct {
	Status string `json:"status"`
}

// Health 检查服务健康状态
//
// 返回服务状态，正常时 Status 为 "UP"
func (c *Client) Health() (*HealthResponse, error) {
	url := fmt.Sprintf("%s/actuator/health", c.BaseURL)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("health check failed with status %d: %s", resp.StatusCode, string(respBody))
	}

	var result HealthResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &result, nil
}

// IsHealthy 检查服务是否健康
//
// 返回 true 表示服务正常，false 表示服务不可用
func (c *Client) IsHealthy() bool {
	health, err := c.Health()
	if err != nil {
		return false
	}
	return health.Status == "UP"
}


// GenerateWord 生成 Word 文档
//
// templateName: 模板文件名（需包含扩展名）
// data: 模板渲染数据
// fileName: 输出文件名（不含扩展名，可选，传空字符串使用默认值）
//
// 返回生成的文档字节数组
func (c *Client) GenerateWord(templateName string, data map[string]any, fileName string) ([]byte, error) {
	req := DocGenRequest{
		TemplateName: templateName,
		Data:         data,
		FileName:     fileName,
	}
	return c.doRequest(req)
}

// GenerateWordWithRequest 使用完整请求结构生成 Word 文档
func (c *Client) GenerateWordWithRequest(req DocGenRequest) ([]byte, error) {
	return c.doRequest(req)
}

// doRequest 执行 HTTP 请求
func (c *Client) doRequest(req DocGenRequest) ([]byte, error) {
	// 序列化请求体
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/doc/word", c.BaseURL)
	httpReq, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/octet-stream")

	// 发送请求
	resp, err := c.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应体
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	// 处理错误响应
	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if err := json.Unmarshal(respBody, &errResp); err != nil {
			return nil, fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(respBody))
		}
		return nil, &errResp
	}

	return respBody, nil
}

// SaveWord 生成 Word 文档并保存到文件
//
// templateName: 模板文件名
// data: 模板渲染数据
// outputPath: 输出文件路径（需包含 .docx 扩展名）
func (c *Client) SaveWord(templateName string, data map[string]any, outputPath string) error {
	doc, err := c.GenerateWord(templateName, data, "")
	if err != nil {
		return err
	}

	return writeFile(outputPath, doc)
}

// BatchGenerateWord 批量生成 Word 文档
//
// templateName: 模板文件名（需包含扩展名）
// dataList: 数据列表，每条数据生成一页
// fileName: 输出文件名（不含扩展名，可选）
//
// 返回包含多页的单个 Word 文档字节数组
func (c *Client) BatchGenerateWord(templateName string, dataList []map[string]any, fileName string) ([]byte, error) {
	req := WordBatchRequest{
		TemplateName: templateName,
		DataList:     dataList,
		FileName:     fileName,
	}
	return c.doBatchRequest(req)
}

// BatchGenerateWordWithRequest 使用完整请求结构批量生成 Word 文档
func (c *Client) BatchGenerateWordWithRequest(req WordBatchRequest) ([]byte, error) {
	return c.doBatchRequest(req)
}

// doBatchRequest 执行批量 Word 生成 HTTP 请求
func (c *Client) doBatchRequest(req WordBatchRequest) ([]byte, error) {
	// 序列化请求体
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/doc/word/batch", c.BaseURL)
	httpReq, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/octet-stream")

	// 发送请求
	resp, err := c.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应体
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	// 处理错误响应
	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if err := json.Unmarshal(respBody, &errResp); err != nil {
			return nil, fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(respBody))
		}
		return nil, &errResp
	}

	return respBody, nil
}

// SaveBatchWord 批量生成 Word 文档并保存到文件
//
// templateName: 模板文件名
// dataList: 数据列表
// outputPath: 输出文件路径（需包含 .docx 扩展名）
func (c *Client) SaveBatchWord(templateName string, dataList []map[string]any, outputPath string) error {
	doc, err := c.BatchGenerateWord(templateName, dataList, "")
	if err != nil {
		return err
	}

	return writeFile(outputPath, doc)
}

// GenerateExcel 生成 Excel 文档
//
// sheetName: 工作表名称（可选，传空字符串使用默认值 "Sheet1"）
// headers: 表头列名列表
// data: 二维数据数组
// fileName: 输出文件名（不含扩展名，可选，传空字符串使用默认值）
//
// 返回生成的 Excel 文档字节数组
func (c *Client) GenerateExcel(sheetName string, headers []string, data [][]any, fileName string) ([]byte, error) {
	req := ExcelGenRequest{
		SheetName: sheetName,
		Headers:   headers,
		Data:      data,
		FileName:  fileName,
	}
	return c.doExcelRequest(req)
}

// GenerateExcelWithRequest 使用完整请求结构生成 Excel 文档
func (c *Client) GenerateExcelWithRequest(req ExcelGenRequest) ([]byte, error) {
	return c.doExcelRequest(req)
}

// doExcelRequest 执行 Excel 生成 HTTP 请求
func (c *Client) doExcelRequest(req ExcelGenRequest) ([]byte, error) {
	// 序列化请求体
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/doc/excel", c.BaseURL)
	httpReq, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/octet-stream")

	// 发送请求
	resp, err := c.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应体
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	// 处理错误响应
	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if err := json.Unmarshal(respBody, &errResp); err != nil {
			return nil, fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(respBody))
		}
		return nil, &errResp
	}

	return respBody, nil
}

// SaveExcel 生成 Excel 文档并保存到文件
//
// sheetName: 工作表名称
// headers: 表头列名列表
// data: 二维数据数组
// outputPath: 输出文件路径（需包含 .xlsx 扩展名）
func (c *Client) SaveExcel(sheetName string, headers []string, data [][]any, outputPath string) error {
	doc, err := c.GenerateExcel(sheetName, headers, data, "")
	if err != nil {
		return err
	}

	return writeFile(outputPath, doc)
}

// FillExcelTemplate 基于模板填充 Excel 文档
//
// templateName: 模板文件名（需包含扩展名）
// data: 单值变量数据（对应模板中的 {variable} 语法）
// listData: 列表数据（对应模板中的 {.field} 语法）
// fileName: 输出文件名（不含扩展名，可选）
//
// 返回填充后的 Excel 文档字节数组
func (c *Client) FillExcelTemplate(templateName string, data map[string]any, listData map[string][]map[string]any, fileName string) ([]byte, error) {
	req := ExcelFillRequest{
		TemplateName: templateName,
		Data:         data,
		ListData:     listData,
		FileName:     fileName,
	}
	return c.doFillRequest(req)
}

// FillExcelTemplateWithRequest 使用完整请求结构填充 Excel 模板
func (c *Client) FillExcelTemplateWithRequest(req ExcelFillRequest) ([]byte, error) {
	return c.doFillRequest(req)
}

// doFillRequest 执行 Excel 模板填充 HTTP 请求
func (c *Client) doFillRequest(req ExcelFillRequest) ([]byte, error) {
	// 序列化请求体
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/doc/excel/fill", c.BaseURL)
	httpReq, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/octet-stream")

	// 发送请求
	resp, err := c.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应体
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	// 处理错误响应
	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if err := json.Unmarshal(respBody, &errResp); err != nil {
			return nil, fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(respBody))
		}
		return nil, &errResp
	}

	return respBody, nil
}

// SaveFilledExcel 填充 Excel 模板并保存到文件
//
// templateName: 模板文件名
// data: 单值变量数据
// listData: 列表数据
// outputPath: 输出文件路径（需包含 .xlsx 扩展名）
func (c *Client) SaveFilledExcel(templateName string, data map[string]any, listData map[string][]map[string]any, outputPath string) error {
	doc, err := c.FillExcelTemplate(templateName, data, listData, "")
	if err != nil {
		return err
	}

	return writeFile(outputPath, doc)
}

// writeFile 写入文件（兼容性封装）
func writeFile(path string, data []byte) error {
	// 使用标准库写入，避免额外依赖
	f, err := createFile(path)
	if err != nil {
		return err
	}
	defer f.Close()

	_, err = f.Write(data)
	return err
}

// createFile 创建文件（需要在 _os.go 中实现，避免循环引用）
var createFile = func(path string) (io.WriteCloser, error) {
	return nil, fmt.Errorf("createFile not implemented, use os.Create")
}
