package de.monkeyworks.buildmonkey.pde.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PushbackReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;

//import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.runner.MessageIds;
import org.eclipse.jdt.internal.junit.runner.RemoteTestRunner;

/**
 * The client side of the RemoteTestRunner. Handles the
 * marshaling of the different messages.
 */
public class PluginTestRunnerClient {
    
    public abstract class ListenerSafeRunnable implements ISafeRunnable {
        public void handleException(Throwable exception) {
            //JUnitCorePlugin.log(exception);
            exception.printStackTrace();
            
        }
    }
    /**
     * A simple state machine to process requests from the RemoteTestRunner
     */
    abstract class ProcessingState {
        abstract ProcessingState readMessage(String message);
    }

    class DefaultProcessingState extends ProcessingState {
        ProcessingState readMessage(String message) {
            if (message.startsWith(MessageIds.TRACE_START)) {
                failedTraceBuffer.setLength(0);
                return traceProcessingState;
            }
            if (message.startsWith(MessageIds.EXPECTED_START)) {
                expectedResultBuffer.setLength(0);
                return expectedProcessingState;
            }
            if (message.startsWith(MessageIds.ACTUAL_START)) {
                actualResultBuffer.setLength(0);
                return actualProcessingState;
            }
            if (message.startsWith(MessageIds.RTRACE_START)) {
                failedRerunTraceBuffer.setLength(0);
                return rerunProcessingState;
            }
            String arg= message.substring(MessageIds.MSG_HEADER_LENGTH);
            if (message.startsWith(MessageIds.TEST_RUN_START)) {
                // version < 2 format: count
                // version >= 2 format: count+" "+version
                int count= 0;
                int v= arg.indexOf(' ');
                if (v == -1) {
                    protocolVersion= "v1"; //$NON-NLS-1$
                    count= Integer.parseInt(arg);
                } else {
                    protocolVersion= arg.substring(v+1);
                    String sc= arg.substring(0, v);
                    count= Integer.parseInt(sc);
                }
                notifyTestRunStarted(count);
                return this;
            }
            if (message.startsWith(MessageIds.TEST_START)) {
                notifyTestStarted(arg);
                return this;
            }
            if (message.startsWith(MessageIds.TEST_END)) {
                notifyTestEnded(arg);
                return this;
            }
            if (message.startsWith(MessageIds.TEST_ERROR)) {
                extractFailure(arg, ITestRunListener2.STATUS_ERROR);
                return this;
            }
            if (message.startsWith(MessageIds.TEST_FAILED)) {
                extractFailure(arg, ITestRunListener2.STATUS_FAILURE);
                return this;
            }
            if (message.startsWith(MessageIds.TEST_RUN_END)) {
                long elapsedTime = Long.parseLong(arg);
                testRunEnded(elapsedTime);
                return this;
            }
            if (message.startsWith(MessageIds.TEST_STOPPED)) {
                long elapsedTime = Long.parseLong(arg);
                notifyTestRunStopped(elapsedTime);
                shutDown();
                return this;
            }
            if (message.startsWith(MessageIds.TEST_TREE)) {
                notifyTestTreeEntry(arg);
                return this;
            }
            if (message.startsWith(MessageIds.TEST_RERAN)) {
                if (hasTestId())
                    scanReranMessage(arg);
                else
                    scanOldReranMessage(arg);
                return this;
            }
            return this;
        }
    }

    /**
     * Base class for states in which messages are appended to an internal
     * string buffer until an end message is read.
     */
    class AppendingProcessingState extends ProcessingState {
        private final StringBuffer fBuffer;
        private String fEndString;

        AppendingProcessingState(StringBuffer buffer, String endString) {
            this.fBuffer= buffer;
            this.fEndString = endString;
        }

        ProcessingState readMessage(String message) {
            if (message.startsWith(fEndString)) {
                entireStringRead();
                return defaultProcessingState;
            }
            fBuffer.append(message);
            if (lastLineDelimiter != null)
                fBuffer.append(lastLineDelimiter);
            return this;
        }

        /**
         * subclasses can override to do special things when end message is read
         */
        void entireStringRead() {
        }
    }

    class TraceProcessingState extends AppendingProcessingState {
        TraceProcessingState() {
            super(failedTraceBuffer, MessageIds.TRACE_END);
        }

        void entireStringRead() {
            notifyTestFailed();
            expectedResultBuffer.setLength(0);
            actualResultBuffer.setLength(0);
        }

        ProcessingState readMessage(String message) {
            if (message.startsWith(MessageIds.TRACE_END)) {
                notifyTestFailed();
                failedTraceBuffer.setLength(0);
                actualResultBuffer.setLength(0);
                expectedResultBuffer.setLength(0);
                return defaultProcessingState;
            }
            failedTraceBuffer.append(message);
            if (lastLineDelimiter != null)
                failedTraceBuffer.append(lastLineDelimiter);
            return this;
        }
    }

    /**
     * The failed trace that is currently reported from the RemoteTestRunner
     */
    private final StringBuffer failedTraceBuffer = new StringBuffer();
    /**
     * The expected test result
     */
    private final StringBuffer expectedResultBuffer = new StringBuffer();
    /**
     * The actual test result
     */
    private final StringBuffer actualResultBuffer = new StringBuffer();
    /**
     * The failed trace of a reran test
     */
    private final StringBuffer failedRerunTraceBuffer = new StringBuffer();


    ProcessingState defaultProcessingState= new DefaultProcessingState();
    ProcessingState traceProcessingState= new TraceProcessingState();
    ProcessingState expectedProcessingState= new AppendingProcessingState(expectedResultBuffer, MessageIds.EXPECTED_END);
    ProcessingState actualProcessingState= new AppendingProcessingState(actualResultBuffer, MessageIds.ACTUAL_END);
    ProcessingState rerunProcessingState= new AppendingProcessingState(failedRerunTraceBuffer, MessageIds.RTRACE_END);
    ProcessingState currentProcessingState= defaultProcessingState;

    /**
     * An array of listeners that are informed about test events.
     */
    private ITestRunListener2[] testRunListeners;

    /**
     * The server socket
     */
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private int communicationPort= -1;
    private PrintWriter clientSocketWriter;
    private PushbackReader clientSocketPushbackReader;
    private String lastLineDelimiter;
    /**
     * The protocol version
     */
    private String protocolVersion;
    /**
     * The failed test that is currently reported from the RemoteTestRunner
     */
    private String currentFailedTest;
    /**
     * The Id of the failed test
     */
    private String currentFailedTestId;
    /**
     * The kind of failure of the test that is currently reported as failed
     */
    private int currentFailureKind;

    private boolean debugEnabled= false;

    /**
     * Reads the message stream from the RemoteTestRunner
     */
    private class ServerConnection extends Thread {
        int serverPort;

        public ServerConnection(int port) {
            super("ServerConnection"); //$NON-NLS-1$
            serverPort= port;
        }

        public void run() {
            try {
                if (debugEnabled)
                    System.out.println("Creating server socket "+serverPort); //$NON-NLS-1$
                serverSocket= new ServerSocket(serverPort);
                clientSocket= serverSocket.accept();
                try {
                    clientSocketPushbackReader= new PushbackReader(new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"))); //$NON-NLS-1$
                } catch (UnsupportedEncodingException e) {
                    clientSocketPushbackReader= new PushbackReader(new BufferedReader(new InputStreamReader(clientSocket.getInputStream())));
                }
                try {
                    clientSocketWriter= new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true); //$NON-NLS-1$
                } catch (UnsupportedEncodingException e1) {
                    clientSocketWriter= new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
                }
                String message;
                while(clientSocketPushbackReader != null && (message= readMessage(clientSocketPushbackReader)) != null)
                    receiveMessage(message);
            } catch (SocketException e) {
                notifyTestRunTerminated();
            } catch (IOException e) {
                //JUnitCorePlugin.log(e);
                e.printStackTrace();
                // fall through
            }
            shutDown();
        }
    }

    /**
     * Start listening to a test run. Start a server connection that
     * the RemoteTestRunner can connect to.
     *
     * @param listeners listeners to inform
     * @param port port on which the server socket will be opened
     */
    public synchronized void startListening(ITestRunListener2[] listeners, int port) {
        testRunListeners= listeners;
        communicationPort= port;
        ServerConnection connection= new ServerConnection(port);
        connection.start();
    }

    /**
     * Requests to stop the remote test run.
     */
    public synchronized void stopTest() {
        if (isRunning()) {
            clientSocketWriter.println(MessageIds.TEST_STOP);
            clientSocketWriter.flush();
        }
    }

    public synchronized void stopWaiting() {
        if (serverSocket != null  && ! serverSocket.isClosed() && clientSocket == null) {
            shutDown(); // will throw a SocketException in Threads that wait in ServerSocket#accept()
        }
    }

    private synchronized void shutDown() {
        if (debugEnabled)
            System.out.println("shutdown "+communicationPort); //$NON-NLS-1$

        if (clientSocketWriter != null) {
            clientSocketWriter.close();
            clientSocketWriter= null;
        }
        try {
            if (clientSocketPushbackReader != null) {
                clientSocketPushbackReader.close();
                clientSocketPushbackReader= null;
            }
        } catch(IOException e) {
        }
        try {
            if (clientSocket != null) {
                clientSocket.close();
                clientSocket= null;
            }
        } catch(IOException e) {
        }
        try{
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket= null;
            }
        } catch(IOException e) {
        }
    }

    public boolean isRunning() {
        return clientSocket != null;
    }

    private String readMessage(PushbackReader in) throws IOException {
        StringBuffer buf= new StringBuffer(128);
        int ch;
        while ((ch= in.read()) != -1) {
            if (ch == '\n') {
                lastLineDelimiter= "\n"; //$NON-NLS-1$
                return buf.toString();
            } else if (ch == '\r') {
                ch= in.read();
                if (ch == '\n') {
                    lastLineDelimiter= "\r\n"; //$NON-NLS-1$
                } else {
                    in.unread(ch);
                    lastLineDelimiter= "\r"; //$NON-NLS-1$
                }
                return buf.toString();
            } else {
                buf.append((char) ch);
            }
        }
        lastLineDelimiter= null;
        if (buf.length() == 0)
            return null;
        return buf.toString();
    }

    private void receiveMessage(String message) {
        currentProcessingState= currentProcessingState.readMessage(message);
    }

    private void scanOldReranMessage(String arg) {
        // OLD V1 format
        // format: className" "testName" "status
        // status: FAILURE, ERROR, OK
        int c= arg.indexOf(" "); //$NON-NLS-1$
        int t= arg.indexOf(" ", c+1); //$NON-NLS-1$
        String className= arg.substring(0, c);
        String testName= arg.substring(c+1, t);
        String status= arg.substring(t+1);
        String testId = className+testName;
        notifyTestReran(testId, className, testName, status);
    }

    private void scanReranMessage(String arg) {
        // format: testId" "className" "testName" "status
        // status: FAILURE, ERROR, OK
        int i= arg.indexOf(' ');
        int c= arg.indexOf(' ', i+1);
        int t; // special treatment, since testName can contain spaces:
        if (arg.endsWith(RemoteTestRunner.RERAN_ERROR)) {
            t= arg.length() - RemoteTestRunner.RERAN_ERROR.length() - 1;
        } else if (arg.endsWith(RemoteTestRunner.RERAN_FAILURE)) {
            t= arg.length() - RemoteTestRunner.RERAN_FAILURE.length() - 1;
        } else if (arg.endsWith(RemoteTestRunner.RERAN_OK)) {
            t= arg.length() - RemoteTestRunner.RERAN_OK.length() - 1;
        } else {
            t= arg.indexOf(' ', c+1);
        }
        String testId= arg.substring(0, i);
        String className= arg.substring(i+1, c);
        String testName= arg.substring(c+1, t);
        String status= arg.substring(t+1);
        notifyTestReran(testId, className, testName, status);
    }

    private void notifyTestReran(String testId, String className, String testName, String status) {
        int statusCode= ITestRunListener2.STATUS_OK;
        if (status.equals("FAILURE")) //$NON-NLS-1$
            statusCode= ITestRunListener2.STATUS_FAILURE;
        else if (status.equals("ERROR")) //$NON-NLS-1$
            statusCode= ITestRunListener2.STATUS_ERROR;

        String trace= ""; //$NON-NLS-1$
        if (statusCode != ITestRunListener2.STATUS_OK)
            trace = failedRerunTraceBuffer.toString();
        // assumption a rerun trace was sent before
        notifyTestReran(testId, className, testName, statusCode, trace);
    }

    private void extractFailure(String arg, int status) {
        String s[]= extractTestId(arg);
        currentFailedTestId= s[0];
        currentFailedTest= s[1];
        currentFailureKind= status;
    }

    /**
     * @param arg test name
     * @return an array with two elements. The first one is the testId, the second one the testName.
     */
    String[] extractTestId(String arg) {
        String[] result= new String[2];
        if (!hasTestId()) {
            result[0]= arg; // use the test name as the test Id
            result[1]= arg;
            return result;
        }
        int i= arg.indexOf(',');
        result[0]= arg.substring(0, i);
        result[1]= arg.substring(i+1, arg.length());
        return result;
    }

    private boolean hasTestId() {
        if (protocolVersion == null) // TODO fix me
            return true;
        return protocolVersion.equals("v2"); //$NON-NLS-1$
    }

    private void notifyTestReran(final String testId, final String className, final String testName, final int statusCode, final String trace) {
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    listener.testReran(testId,
                                className, testName, statusCode, trace,
                                nullifyEmpty(expectedResultBuffer), nullifyEmpty(actualResultBuffer));
                }
            });
        }
    }

    private void notifyTestTreeEntry(final String treeEntry) {
        for (int i= 0; i < testRunListeners.length; i++) {
            ITestRunListener2 listener= testRunListeners[i];
            if (!hasTestId())
                listener.testTreeEntry(fakeTestId(treeEntry));
            else
                listener.testTreeEntry(treeEntry);
        }
    }

    private String fakeTestId(String treeEntry) {
        // extract the test name and add it as the testId
        int index0= treeEntry.indexOf(',');
        String testName= treeEntry.substring(0, index0).trim();
        return testName+","+treeEntry; //$NON-NLS-1$
    }

    private void notifyTestRunStopped(final long elapsedTime) {
//        if (JUnitCorePlugin.isStopped())
//            return;
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    listener.testRunStopped(elapsedTime);
                }
            });
        }
    }

    private void testRunEnded(final long elapsedTime) {
//        if (JUnitCorePlugin.isStopped())
//            return;
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    listener.testRunEnded(elapsedTime);
                }
            });
        }
    }

    private void notifyTestEnded(final String test) {
//        if (JUnitCorePlugin.isStopped())
//            return;
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    String s[]= extractTestId(test);
                    listener.testEnded(s[0], s[1]);
                }
            });
        }
    }

    private void notifyTestStarted(final String test) {
//        if (JUnitCorePlugin.isStopped())
//            return;
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    String s[]= extractTestId(test);
                    listener.testStarted(s[0], s[1]);
                }
            });
        }
    }

    private void notifyTestRunStarted(final int count) {
//        if (JUnitCorePlugin.isStopped())
//            return;
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    listener.testRunStarted(count);
                }
            });
        }
    }

    private void notifyTestFailed() {
//        if (JUnitCorePlugin.isStopped())
//            return;
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    listener.testFailed(currentFailureKind, currentFailedTestId,
                            currentFailedTest, failedTraceBuffer.toString(), nullifyEmpty(expectedResultBuffer), nullifyEmpty(actualResultBuffer));
                }
            });
        }
    }

    /**
     * Returns a comparison result from the given buffer.
     * Removes the terminating line delimiter.
     * 
     * @param buf the comparison result
     * @return the result or <code>null</code> if empty
     * @since 3.7
     */
    private static String nullifyEmpty(StringBuffer buf) {
        int length= buf.length();
        if (length == 0)
            return null;
        
        char last= buf.charAt(length - 1);
        if (last == '\n') {
            if (length > 1 && buf.charAt(length - 2) == '\r')
                return buf.substring(0, length - 2);
            else
                return buf.substring(0, length - 1);
        } else if (last == '\r') {
            return buf.substring(0, length - 1);
        }
        return buf.toString();
    }
    
    private void notifyTestRunTerminated() {
        // fix for 77771 RemoteTestRunnerClient doing work after junit shutdown [JUnit]
//        if (JUnitCorePlugin.isStopped())
//            return;
        for (int i= 0; i < testRunListeners.length; i++) {
            final ITestRunListener2 listener= testRunListeners[i];
            SafeRunner.run(new ListenerSafeRunnable() {
                public void run() {
                    listener.testRunTerminated();
                }
            });
        }
    }

    public void rerunTest(String testId, String className, String testName) {
        if (isRunning()) {
            actualResultBuffer.setLength(0);
            expectedResultBuffer.setLength(0);
            clientSocketWriter.println(MessageIds.TEST_RERUN+testId+" "+className+" "+testName); //$NON-NLS-1$ //$NON-NLS-2$
            clientSocketWriter.flush();
        }
    }
}
