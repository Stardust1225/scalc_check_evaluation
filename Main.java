import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String args[]) {
        try {
            new Main();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    String linterPattern = "\\[warn\\]\\s.*\\.scala:[0-9]*:\\s\\[[a-zA-Z]*\\].*";
    String scapegoatPattern = "\\[warn\\]\\s.*\\.scala:[0-9]*:\\s\\[scapegoat\\].*";

    List<String> checkList, successList;

    public Main() throws Exception {
        checkList = Files.readAllLines(Paths.get("check.txt"));
        successList = Files.readAllLines(Paths.get("success.txt"));

        ExecutorService pool = Executors.newFixedThreadPool(10);
        ExecutorCompletionService<String> service = new ExecutorCompletionService<>(pool);

        int count = 0;

        for (File file : new File("D:\\project\\evaluate-scala-checks\\data\\combina_log").listFiles()) {
            boolean inList = false;
            for (String s : successList)
                if (file.getAbsolutePath().contains(s)) {
                    inList = true;
                    break;
                }
            if (!inList) {
                String repoPath = "d:\\data\\" + file.getName().replace(".txt", "");
                if (new File(repoPath).exists()) {
                    service.submit(new OneProject(file, count));
                    count++;
                }
            }
        }

        for (int i = 0; i < count; i++)
            service.take().get();

        pool.shutdown();

    }

    class OneProject implements Callable<String> {

        File warningFile;
        int count = 0;

        public OneProject(File file, int count) {
            this.warningFile = file;
            this.count = count;
        }

        @Override
        public String call() throws Exception {

            String repoPath = "d:\\data" + warningFile.getName().replace(".txt", "");

            File warningTxt = new File(repoPath + "/warning.txt");

            warningTxt.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(warningTxt);
            outputStream.write(("first_start_test" + "\r\n").getBytes());
            outputStream.write(("first_start_test" + "\r\n").getBytes());
            outputStream.write(("first_start_test" + "\r\n").getBytes());
            outputStream.write(("first_start_test" + "\r\n").getBytes());
            outputStream.close();

            System.out.println(new Date() + "\t" + repoPath + "\tStart compilation");

            ProcessBuilder builder = new ProcessBuilder(
                    "/bin/sh",
                    "-c",
                    "java " +
                            " -Dsbt.global.base=/pub/data/zhangx/data/relylib" + (count % 10) + "/.sbt " +
                            " -Dsbt.ivy.home=/pub/data/zhangx/data/relylib" + (count % 10) + "/.ivy2 " +
                            " -jar sbt-launch.jar ~compile");

            builder.directory(new File(repoPath));
            builder.redirectErrorStream(true);

            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String readLine;

            while ((readLine = reader.readLine()) != null)
                if (readLine.contains("Waiting for source changes"))
                    break;

            List<String> warningContent = Files.readAllLines(Paths.get(warningFile.getAbsolutePath()));

            File timeFile = new File("/home/zhangxin19/combina_time_fp/" + warningFile.getName());
            timeFile.createNewFile();
            FileOutputStream timeOutputStream = new FileOutputStream(timeFile);

            System.out.println(new Date() + "\t" + repoPath + "\tStart classify");

            for (int i = 0; i < warningContent.size(); i++) {
                String s = warningContent.get(i);
                String check = null, description = null, filepath = null, line = null;
                if (s.matches(linterPattern) && !s.contains("scapegoat")) {
                    int leftIndex = s.indexOf("[warn] ") + "[warn] ".length();
                    int rightIndex = s.indexOf(".scala") + ".scala".length();
                    filepath = s.substring(leftIndex, rightIndex).replace("D:\\data\\", "");

                    leftIndex = s.indexOf(":", rightIndex) + 1;
                    rightIndex = s.indexOf(":", leftIndex + 1);
                    line = s.substring(leftIndex, rightIndex);

                    leftIndex = s.indexOf("[", rightIndex) + 1;
                    rightIndex = s.indexOf("]", leftIndex);
                    check = s.substring(leftIndex, rightIndex);

                    description = s.substring(rightIndex + 1).trim();

                    StringBuffer warningcode = new StringBuffer();
                    while (true) {
                        i++;
                        String s1 = warningContent.get(i);
                        if (!s1.contains("[warn]"))
                            warningcode.append("\n");
                        else if (s1.substring("[warn]".length()).trim().equals("^"))
                            break;
                        else
                            warningcode.append(s1.substring("[warn]".length()).trim() + "\n");
                    }

                    int x = filepath.indexOf("\\");
                    x = filepath.indexOf("\\", x + 1);
                    filepath = filepath.substring(x + 1);
                } else if (s.matches(scapegoatPattern)) {
                    int leftIndex = s.indexOf("[warn] ") + "[warn] ".length();
                    int rightIndex = s.indexOf(".scala") + ".scala".length();
                    filepath = s.substring(leftIndex, rightIndex).replace("D:\\data\\", "");

                    leftIndex = s.indexOf(":", rightIndex) + 1;
                    rightIndex = s.indexOf(":", leftIndex + 1);
                    line = s.substring(leftIndex, rightIndex);

                    leftIndex = s.indexOf("[scapegoat]") + "[scapegoat]".length();
                    check = s.substring(leftIndex).trim();

                    description = warningContent.get(i + 1).substring("[warn]".length()).trim();

                    StringBuffer warningcode = new StringBuffer();
                    i++;
                    while (true) {
                        i++;
                        String s1 = warningContent.get(i);
                        if (!s1.contains("[warn]"))
                            warningcode.append("\n");
                        else if (s1.substring("[warn]".length()).trim().equals("^"))
                            break;
                        else
                            warningcode.append(s1.substring("[warn]".length()).trim() + "\n");
                    }

                    int x = filepath.indexOf("\\");
                    x = filepath.indexOf("\\", x + 1);
                    filepath = filepath.substring(x + 1);
                }

                if (!checkList.contains(check))
                    continue;

                //需要同步修改warning在的文件和warning.t
                warningTxt = new File(repoPath + "/warning.txt");
                warningTxt.createNewFile();
                outputStream = new FileOutputStream(warningTxt);
                outputStream.write((check + "\r\n").getBytes());
                outputStream.write((description + "\r\n").getBytes());
                outputStream.write((filepath + "\r\n").getBytes());
                outputStream.write((line + "\r\n").getBytes());
                outputStream.close();

                File sourcecode = new File(repoPath + "/" + filepath.replace("\\", "/"));
                FileOutputStream sourcecodeOutput = new FileOutputStream(sourcecode, true);
                sourcecodeOutput.write(("// for warning: " + check + "  " + line + "\r\n").getBytes());
                sourcecodeOutput.close();

                long startTime = System.currentTimeMillis();
                long endtime = 0;

                boolean isThisWarningRecord = false;

                while ((readLine = reader.readLine()) != null) {
                    if (!isThisWarningRecord && readLine.contains("[error]")) {
                        if (!readLine.contains("This warning is true")) {
                            //wrongOutputStream.write((check + "\t" + line + "\t" + filepath + "\r\n").getBytes());
                            endtime = System.currentTimeMillis();
                            timeOutputStream.write(("true\t"
                                    + check + "\t"
                                    + line + "\t"
                                    + filepath + "\t"
                                    + (endtime - startTime) + "\r\n").getBytes());
                        } else {
                            endtime = System.currentTimeMillis();
                            timeOutputStream.write(("false\t"
                                    + check + "\t"
                                    + line + "\t"
                                    + filepath + "\t"
                                    + (endtime - startTime) + "\r\n").getBytes());
                        }
                        isThisWarningRecord = true;
                    }

                    if (readLine.contains("Waiting for source changes") || isThisWarningRecord)
                        break;
                }

                if (!isThisWarningRecord) {
                    endtime = System.currentTimeMillis();
                    timeOutputStream.write(("false\t"
                            + check + "\t"
                            + line + "\t"
                            + filepath + "\t"
                            + (endtime - startTime) + "\r\n").getBytes());
                }
            }

            timeOutputStream.close();
            reader.close();

            process.destroy();

            System.out.println(new Date() + "\t" + warningFile.getAbsolutePath() + "\tEnd");

            return repoPath;
        }
    }
}
