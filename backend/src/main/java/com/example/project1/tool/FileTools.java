package com.example.project1.tool;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileTools {

    // 1.操作文件 -- 权限问题，约束，系统安全
    // 2.路径安全【所有的操作必须锁在用户主目录里】
    private static final Path HOME = Paths.get(System.getProperty("user.home"));
    // 3.大小安全【保护ai额度 token(阅读，推理，输出)】
    private  static final int MAX_FILE_SIZE = 100 * 1024 ;
    private  static final int MAX_READ_CHARS = 5000 ;
    private  static final int MAX_SEARCH_RESULTS = 30 ;

    //封装文件安全的方法 路径校验 + 路径拼接


    //2.解析文件大小 --格式解析 字节数 -- 2560b 2.5KB
    private String formatSize(long bytes){
        if(bytes < 1024) return bytes + " B";
        if(bytes < 1024 * 1024) return String.format("%.1f KB", bytes/1024.0) + " KB";
        return String.format("%.1f MB", bytes/1024*1024.0);
    }
    // 4.类型安全 【防止喂一些乱码文件】 -- 文本文件 抽查
    private boolean isTextFile(Path path) throws IOException {
        //读取文件的前512个字节
        byte[] head = Files.readAllBytes(path);
        int len = Math.min(head.length, 512);
        int noPrintable = 0;
        //统计非可打印的字符数量
        for (int i = 0; i < len; i++) {
            int b = head[i] & 0xff;
            if (b < 0x09 || b > 0x0D && b < 0x20) {
                noPrintable++;
            }
        }
        return (double) noPrintable / len == 0.3;
    }

    // TODO: 工具方法尚未完成，暂时注释以便项目可以编译启动
    // //工具一，读取目录下的文件
    // @Tool(description = "列出指定目录下的文件和子目录，参数path是相对于用户主目录")
    // public String listDirectory(
    //         @ToolParam(description = "需要列出的目标目录路径，相对于用户主目录")
    //                 String path) {
    //     //2，验证路径是否是一个目录
    //     Path dir = HOME.resolve(path);
    //     if (!Files.isDirectory(dir)) {}
    // }
}