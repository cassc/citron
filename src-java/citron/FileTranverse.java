package citron;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class FileTranverse {
    private static void timedPrinter(Object o) {
        System.out.println(LocalTime.now() +" " + o);
    }

    public static List<Map<String, Object>> listDiretory(String parent, File file){
        try(var files = Files.list(file.toPath())){
           return files
                    .sorted(Comparator.naturalOrder())
                    .map(f->{
                        boolean isdir = Files.isDirectory(f);
                        var m = new HashMap<String, Object>();
                        m.put("path", String.format("%s/%s", parent, f.getFileName().toString()));
                        m.put("isdir", isdir);
                        if (!isdir){
                            try {
                                m.put("size", Files.size(f));
                            } catch (IOException e) {
                            }
                        }
                        m.put("exists", true);
                        try {
                            m.put("mime", Files.probeContentType(f));
                        } catch (IOException e) {
                            // ignore mime not detected
                        }
                        return m;
                    })
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public static void main(String[] args) {
        var path = "/var/www/public/svg/logos";

        var file = new File(path);
        final int maxCount = 1000001;
        timedPrinter("File list start");
        var fileList = file.listFiles();
        timedPrinter("file list end");
        timedPrinter("files found: " + fileList.length);

        timedPrinter("Sorted by name");
        var files = Arrays.stream(fileList)
                .sorted(Comparator.comparing(File::getName))
                .map(f->{
                    var m = new HashMap<String, Object>();
                    m.put("filename", f.getName());
                    m.put("isdir", f.isDirectory());
                    m.put("size", f.length());
                    m.put("exists", true);
                    try {
                        m.put("mime", Files.probeContentType(f.toPath()));
                    } catch (IOException e) {
                        // ignore mime not detected
                    }
                    return m;
                })
                .collect(Collectors.toList());
        timedPrinter("Sorted out: " + files.get(files.size()-1));

        //Arrays.stream(fileList).forEach(FileTranverse::timedPrinter);

        timedPrinter("list dir start");
        var fs = listDiretory(path, file);
        timedPrinter("list dir end");
        timedPrinter("last: "+ fs.get(fs.size()-1));

    }
}
