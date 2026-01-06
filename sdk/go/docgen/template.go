package docgen

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
)

// UploadResponse 上传模板响应
type UploadResponse struct {
	Success  bool   `json:"success"`
	Message  string `json:"message"`
	FileName string `json:"fileName"`
}

// ListTemplatesResponse 模板列表响应
type ListTemplatesResponse struct {
	Success   bool     `json:"success"`
	Count     int      `json:"count"`
	Templates []string `json:"templates"`
}

// UploadTemplate 上传模板文件
//
// filePath: 本地模板文件路径
//
// 返回上传结果，包含保存后的文件名
func (c *Client) UploadTemplate(filePath string) (*UploadResponse, error) {
	// 打开文件
	file, err := os.Open(filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	// 创建 multipart 表单
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	// 添加文件字段
	part, err := writer.CreateFormFile("file", filepath.Base(filePath))
	if err != nil {
		return nil, fmt.Errorf("failed to create form file: %w", err)
	}

	_, err = io.Copy(part, file)
	if err != nil {
		return nil, fmt.Errorf("failed to copy file content: %w", err)
	}

	err = writer.Close()
	if err != nil {
		return nil, fmt.Errorf("failed to close writer: %w", err)
	}

	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/template/upload", c.BaseURL)
	req, err := http.NewRequest(http.MethodPost, url, body)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", writer.FormDataContentType())

	// 发送请求
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应
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

	// 解析成功响应
	var result UploadResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &result, nil
}

// UploadTemplateFromBytes 从字节数组上传模板文件
//
// data: 文件内容字节数组
// filename: 文件名（需包含扩展名）
func (c *Client) UploadTemplateFromBytes(data []byte, filename string) (*UploadResponse, error) {
	// 创建 multipart 表单
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	// 添加文件字段
	part, err := writer.CreateFormFile("file", filename)
	if err != nil {
		return nil, fmt.Errorf("failed to create form file: %w", err)
	}

	_, err = part.Write(data)
	if err != nil {
		return nil, fmt.Errorf("failed to write file content: %w", err)
	}

	err = writer.Close()
	if err != nil {
		return nil, fmt.Errorf("failed to close writer: %w", err)
	}

	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/template/upload", c.BaseURL)
	req, err := http.NewRequest(http.MethodPost, url, body)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", writer.FormDataContentType())

	// 发送请求
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应
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

	// 解析成功响应
	var result UploadResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &result, nil
}

// ListTemplates 获取所有模板文件列表
//
// 返回模板文件名数组
func (c *Client) ListTemplates() ([]string, error) {
	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/template/list", c.BaseURL)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// 发送请求
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应
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

	// 解析成功响应
	var result ListTemplatesResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return result.Templates, nil
}

// ListTemplatesWithDetails 获取模板列表（包含详细信息）
//
// 返回完整的响应结构，包含 success、count 和 templates
func (c *Client) ListTemplatesWithDetails() (*ListTemplatesResponse, error) {
	// 构建 HTTP 请求
	url := fmt.Sprintf("%s/api/v1/template/list", c.BaseURL)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// 发送请求
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应
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

	// 解析成功响应
	var result ListTemplatesResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &result, nil
}

// DeleteResponse 删除模板响应
type DeleteResponse struct {
	Success  bool   `json:"success"`
	Message  string `json:"message"`
	FileName string `json:"fileName"`
}

// DeleteTemplate 删除模板文件
//
// templateName: 要删除的模板文件名
//
// 返回删除结果
func (c *Client) DeleteTemplate(templateName string) (*DeleteResponse, error) {
	// 构建 HTTP 请求（对模板名称进行 URL 编码，支持中文和特殊字符）
	apiURL := fmt.Sprintf("%s/api/v1/template/%s", c.BaseURL, url.PathEscape(templateName))
	req, err := http.NewRequest(http.MethodDelete, apiURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// 发送请求
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应
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

	// 解析成功响应
	var result DeleteResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &result, nil
}

// DownloadTemplate 下载模板文件
//
// templateName: 模板文件名
//
// 返回模板文件的字节数组
func (c *Client) DownloadTemplate(templateName string) ([]byte, error) {
	// 构建 HTTP 请求（对模板名称进行 URL 编码，支持中文和特殊字符）
	apiURL := fmt.Sprintf("%s/api/v1/template/download/%s", c.BaseURL, url.PathEscape(templateName))
	req, err := http.NewRequest(http.MethodGet, apiURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// 发送请求
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应
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

// SaveTemplate 下载模板并保存到本地文件
//
// templateName: 远程模板文件名
// outputPath: 本地保存路径
func (c *Client) SaveTemplate(templateName, outputPath string) error {
	content, err := c.DownloadTemplate(templateName)
	if err != nil {
		return err
	}

	return os.WriteFile(outputPath, content, 0644)
}
