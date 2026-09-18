# Prismod 示例模组资源包

这是一个最小可用的 Prismod 自定义模组资源包，提供 `example:debug` 反色滤镜。它不是 Minecraft 原版资源包，不需要 `pack.mcmeta`。

资源包根目录必须包含固定文件名 `prismod.meta.json`，并声明一个合法且未被占用的命名空间：

```json
{
  "namespace": "example"
}
```

将此目录直接放入游戏实例的 `config/prismod/resourcepacks/`，或压缩为 ZIP 后放入同一目录。启动游戏或执行 F3+T 后，Prismod 会自动发现其中的滤镜，不需要在原版资源包界面中启用。

资源文件位于 `assets/example/shaders/post/debug.json`。发现后可在 Prismod 滤镜设置中看到 `example:debug`。
