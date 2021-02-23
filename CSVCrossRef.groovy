import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor

import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

class CSVCrossRef extends Script {
    private static final String NO_FILE = ''
    enum CrossRefMode { FULL, FILE_A_STARTS_WITH_B_SUBSTRING, FILE_B_STARTS_WITH_A_SUBSTRING }

    @Override
    Object run() {
        def cli = new CliBuilder(usage: 'CrossRef [options]', header: 'Options:')
        cli.width = 120
        cli.h(longOpt: 'help', 'Shows useful information')
        cli._(longOpt: 'fileA', args: 1, argName: 'fileA', 'Filename A')
        cli._(longOpt: 'fileB', args: 1, argName: 'fileB', 'Filename B')
        cli._(longOpt: 'fileAHasHeader', args: 1, argName: 'fileAHeader', 'FileA has header (y/n)')
        cli._(longOpt: 'fileBHasHeader', args: 1, argName: 'fileBHeader', 'FileB has header (y/n)')
        cli._(longOpt: 'fileAXrefCol', args: 1, argName: 'fileAXrefCol', 'File A cross-reference column')
        cli._(longOpt: 'fileBXrefCol', args: 1, argName: 'fileBXrefCol', 'File B cross-reference column')
        cli._(longOpt: 'xrefMode', args: 1, argName: 'xrefMode', 'Cross-reference mode')
        cli._(longOpt: 'outfile', args: 1, argName: 'outfile', 'Output file')

        OptionAccessor options = cli.parse(this.args)

        if (!options) {
            return
        }

        if (options.h) {
            cli.usage()
            return
        }

        // note that options.'xxxx' returns False when value isn't present.  Goofy, but handled by ScriptUtils.Prompt methods.
        List<List<String>> fileAContents = promptReadFile('Filename A?', options.'fileB')
        List<List<String>> fileBContents = promptReadFile('Filename B?', options.'fileB')

        final boolean fileAHasHeader = ScriptUtils.Prompt('File A has header (y/n)?', /[YyNn]/, options.'fileAHeader').toLowerCase().equals('y')
        final boolean fileBHasHeader = ScriptUtils.Prompt('File B has header (y/n)?', /[YyNn]/, options.'fileBHeader').toLowerCase().equals('y')

        List<XrefInfo> xrefInfos = new ArrayList<>()

        while (true) {
            Integer fileAXrefColumn = promptForListIndex("File A cross-reference ${xrefInfos.size +1} column?",
                    fileAContents.get(0), (Boolean) options.'fileAXrefCol', xrefInfos.size() > 0)
            if (fileAXrefColumn == null) {
                break
            }
            int fileBXrefColumn = promptForListIndex("File B cross-reference ${xrefInfos.size + 1} column?",
                    fileBContents.get(0), (Boolean) options.'fileBXrefCol')

            CrossRefMode xrefMode = CrossRefMode.values()[promptForListIndex(
                    "Cross-reference mode ${xrefInfos.size() + 1} for ${fileAXrefColumn} -> ${fileBXrefColumn}?",
                    Arrays.asList(CrossRefMode.values()), options.'xrefMode')]
            xrefInfos.add(new XrefInfo(fileAXrefColumn, fileBXrefColumn, xrefMode))
        }

        String outfile = ScriptUtils.PromptDefault('What is the output file (optional, .csv)?', /(|.+\.csv)/, NO_FILE, options.'outfile' )

        // Generate a new heading for combined results
        List<String> headings = new ArrayList<>()
        final AtomicInteger colNum = new AtomicInteger(0)
        headings.addAll(fileAContents.get(0).stream().map(
                header -> "A.${(fileAHasHeader) ? header : colNum.incrementAndGet()}").collect(Collectors.toList()))
        colNum.set(0)
        headings.addAll(fileBContents.get(0).stream().map(
                header -> "B.${(fileBHasHeader) ? header : colNum.incrementAndGet()}").collect(Collectors.toList()))

        println()

        // output headings
        File file = null;
        if (outfile.equals(NO_FILE)) {
            println(headings.join(','))
        } else {
            file = new File(outfile)
            file.write "${headings.join(',')}\n"
        }

        for (int indexA = fileAHasHeader ? 1 : 0; indexA < fileAContents.size(); indexA++) {
            // only update processing if not going to console
            if (file != null) {
                System.out.print("\rProcessing input ${indexA} / ${fileAContents.size()}...")
            }
            for (int indexB = fileBHasHeader ? 1 : 0; indexB < fileAContents.size(); indexB++) {
                // determine if all requested columns match
                boolean matches = true
                for (XrefInfo xrefInfo: xrefInfos) {
                    String xrefAMatch = fileAContents.get(indexA)[xrefInfo.colA]
                    String xrefBMatch = fileAContents.get(indexB)[xrefInfo.colB]
                    switch (xrefInfo.xrefMode) {
                        case CrossRefMode.FULL:
                            matches = xrefAMatch.equals(xrefBMatch)
                            break
                        case CrossRefMode.FILE_A_STARTS_WITH_B_SUBSTRING:
                            matches = xrefAMatch.startsWith(xrefBMatch)
                            break
                        case CrossRefMode.FILE_B_STARTS_WITH_A_SUBSTRING:
                            matches = xrefBMatch.startsWith(xrefAMatch)
                            break
                    }

                    if (!matches) {
                        break
                    }
                }

                // If it does match, combine the rows
                if (matches) {
                    if (file == null) {
                        print(fileAContents.get(indexA).join(','))
                        print(',')
                        println(fileBContents.get(indexA).join(','))
                    } else {
                        file.append(fileAContents.get(indexA).join(','))
                        file.append(',')
                        file.append(fileBContents.get(indexA).join(','))
                        file.append('\n')
                    }
                }
            }
        }
        // only update processing if not going to console
        if (file != null) {
            println('\nProcessing complete.')
        }
    }

    List<List<String>> promptReadFile(String question, Boolean optionSpec) {
        while (true) {
            String filename = ScriptUtils.Prompt(question, /(|.+\.csv)/, optionSpec)

            List<List<String>> fileContents = new ArrayList<>()
            Integer itemsPerLine = null

            try {
                Scanner scanner = new Scanner(new File(filename))
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim()
                    // skip empty lines
                    if (line.length() <= 0) {
                        continue
                    }

                    // Break up line into items
                    // TODO: Handle quoted values correctly
                    List<String> items = Arrays.asList(line.split(',', -1))
                    if (itemsPerLine == null) {
                        itemsPerLine = items.size()
                    }

                    if (itemsPerLine != items.size()) {
                        throw new RuntimeException("File has inconsistent values on lines")
                    }

                    fileContents.add(items)
                }
                scanner.close()

                if (fileContents.size() > 0) {
                    if (fileContents.get(0).size() > 0) {
                        return fileContents
                    }
                }
                System.err.println("File ${filename} is empty or empty first line.")
            } catch (Exception e) {
                System.err.println("Could not read in file: ${filename} because ${e.getMessage()}")
            }
        }
    }

    Integer promptForListIndex(String prompt, List<String> values, Boolean optionSpec, Boolean allowEmpty = false) {
        System.out.println("Values:")
        while (true) {
            for (int i = 0; i < values.size(); i++) {
                System.out.println("   ${i + 1}: ${values[i]}")
            }
            def validationRegex = (allowEmpty) ? /|[0-9]+/ : /[0-9]+/
            String enteredValue = ScriptUtils.Prompt(prompt, validationRegex, optionSpec)
            if (enteredValue.length() == 0) {
                return null
            }

            int selected = enteredValue.toInteger()
            if (selected > 0 && selected <= values.size()) {
                return selected - 1
            }

            System.err.println("You must pick a column between 1 - ${values.size()}")
        }
    }

    class XrefInfo {
        int colA
        int colB
        CrossRefMode xrefMode

        XrefInfo(int colA, int colB, CrossRefMode xrefMode) {
            this.colA = colA
            this.colB = colB
            this.xrefMode = xrefMode
        }
    }
}