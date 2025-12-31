package docgen

import "os"

func init() {
	// 实现 createFile 函数
	createFile = func(path string) (interface {
		Write([]byte) (int, error)
		Close() error
	}, error) {
		return os.Create(path)
	}
}
