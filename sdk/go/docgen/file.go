package docgen

import (
	"io"
	"os"
)

func init() {
	// 实现 createFile 函数，返回 io.WriteCloser 接口
	createFile = func(path string) (io.WriteCloser, error) {
		return os.Create(path)
	}
}
