package jenkins.plugins.hipchat;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.logging.Logger;

import static jenkins.plugins.hipchat.NotificationType.*;

@SuppressWarnings({"unchecked"})
public class HipChatNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(HipChatNotifier.class.getName());
    private String room;
    private boolean startNotification;
    private boolean notifySuccess;
    private boolean notifyAborted;
    private boolean notifyNotBuilt;
    private boolean notifyUnstable;
    private boolean notifyFailure;
    private boolean notifyBackToNormal;

    @DataBoundConstructor
    public HipChatNotifier(String room, boolean startNotification, boolean notifySuccess, boolean notifyAborted,
            boolean notifyNotBuilt, boolean notifyUnstable, boolean notifyFailure, boolean notifyBackToNormal) {
        this.room = room;
        this.startNotification = startNotification;
        this.notifySuccess = notifySuccess;
        this.notifyAborted = notifyAborted;
        this.notifyNotBuilt = notifyNotBuilt;
        this.notifyUnstable = notifyUnstable;
        this.notifyFailure = notifyFailure;
        this.notifyBackToNormal = notifyBackToNormal;
    }

    public boolean isStartNotification() {
        return startNotification;
    }

    public void setStartNotification(boolean startNotification) {
        this.startNotification = startNotification;
    }

    public boolean isNotifySuccess() {
        return notifySuccess;
    }

    public void setNotifySuccess(boolean notifySuccess) {
        this.notifySuccess = notifySuccess;
    }

    public boolean isNotifyAborted() {
        return notifyAborted;
    }

    public void setNotifyAborted(boolean notifyAborted) {
        this.notifyAborted = notifyAborted;
    }

    public boolean isNotifyNotBuilt() {
        return notifyNotBuilt;
    }

    public void setNotifyNotBuilt(boolean notifyNotBuilt) {
        this.notifyNotBuilt = notifyNotBuilt;
    }

    public boolean isNotifyUnstable() {
        return notifyUnstable;
    }

    public void setNotifyUnstable(boolean notifyUnstable) {
        this.notifyUnstable = notifyUnstable;
    }

    public boolean isNotifyFailure() {
        return notifyFailure;
    }

    public void setNotifyFailure(boolean notifyFailure) {
        this.notifyFailure = notifyFailure;
    }

    public boolean isNotifyBackToNormal() {
        return notifyBackToNormal;
    }

    public void setNotifyBackToNormal(boolean notifyBackToNormal) {
        this.notifyBackToNormal = notifyBackToNormal;
    }

    public String getRoom() {
        return StringUtils.isBlank(room) ? getDescriptor().getRoom() : room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    public String getServer() {
        return getDescriptor().getServer();
    }

    public String getAuthToken() {
        return getDescriptor().getToken();
    }

    public String getSendAs() {
        return getDescriptor().getSendAs();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public HipChatService newHipChatService() {
        return new StandardHipChatService(getServer(), getAuthToken(), getRoom(), getSendAs());
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if (isNotificationEnabled(STARTED)) {
            logger.fine("Creating build start notification");
            newHipChatService().publish(STARTED.getMessage(build), STARTED.getColor());
        }
        return super.prebuild(build, listener);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        logger.fine("Creating build completed notification");
        Result result = build.getResult();
        Result previousResult = findPreviousBuildResult(build);

        NotificationType notificationType = determineNotificationType(previousResult, result);
        if (isNotificationEnabled(notificationType)) {
            newHipChatService().publish(notificationType.getMessage(build), notificationType.getColor());
        }

        return super.perform(build, launcher, listener);
    }

    private Result findPreviousBuildResult(AbstractBuild<?,?> build) {
        do {
            build = build.getPreviousBuild();
            if (build == null || build.isBuilding()) {
                return null;
            }
        } while (build.getResult() == Result.ABORTED || build.getResult() == Result.NOT_BUILT);
        return build.getResult();
    }

    private NotificationType determineNotificationType(Result previousResult, Result result) {
        if (result == Result.ABORTED) {
            return ABORTED;
        } else if (result == Result.FAILURE) {
            return FAILURE;
        } else if (result == Result.NOT_BUILT) {
            return NOT_BUILT;
        } else if (result == Result.UNSTABLE) {
            return UNSTABLE;
        } else if (result == Result.SUCCESS) {
            if (previousResult == Result.FAILURE) {
                return BACK_TO_NORMAL;
            } else {
                return SUCCESS;
            }
        }

        return UNKNOWN;
    }

    private boolean isNotificationEnabled(NotificationType type) {
        switch (type) {
            case ABORTED:
                return notifyAborted;
            case BACK_TO_NORMAL:
                return notifyBackToNormal;
            case FAILURE:
                return notifyFailure;
            case NOT_BUILT:
                return notifyNotBuilt;
            case STARTED:
                return startNotification;
            case SUCCESS:
                return notifySuccess;
            case UNSTABLE:
                return notifyUnstable;
            default:
                return false;
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String server = "api.hipchat.com";
        private String token;
        private String room;
        private String sendAs = "Jenkins";
        private static int testNotificationCount = 0;

        public DescriptorImpl() {
            load();
        }

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getRoom() {
            return room;
        }

        public void setRoom(String room) {
            this.room = room;
        }

        public String getSendAs() {
            return sendAs;
        }

        public void setSendAs(String sendAs) {
            this.sendAs = sendAs;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest request, JSONObject formData) throws FormException {
            request.bindJSON(this, formData);

            save();
            return super.configure(request, formData);
        }

        public FormValidation doSendTestNotification(@QueryParameter("hipchat.server") String server,
                @QueryParameter("hipchat.token") String token, @QueryParameter("hipchat.room") String room,
                @QueryParameter("hipchat.sendAs") String sendAs) {
            HipChatService service = new StandardHipChatService(server, token, room, sendAs);
            service.publish(Messages.TestNotification(++testNotificationCount));
            return FormValidation.ok(Messages.TestNotificationSent());
        }

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }
    }

    /**
     * The settings defined here have been moved to the {@link HipChatNotifier} configuration (shows up under the Post
     * Build task view).
     *
     * @deprecated The plugin configuration should be stored in {@link HipChatNotifier}. This class only exists, so
     * configurations can be migrated for the build jobs.
     */
    @Deprecated
    public static class HipChatJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {
        private final String room;
        private final boolean startNotification;
        private final boolean notifySuccess;
        private final boolean notifyAborted;
        private final boolean notifyNotBuilt;
        private final boolean notifyUnstable;
        private final boolean notifyFailure;
        private final boolean notifyBackToNormal;


        @DataBoundConstructor
        public HipChatJobProperty(String room,
                                  boolean startNotification,
                                  boolean notifyAborted,
                                  boolean notifyFailure,
                                  boolean notifyNotBuilt,
                                  boolean notifySuccess,
                                  boolean notifyUnstable,
                                  boolean notifyBackToNormal) {
            this.room = room;
            this.startNotification = startNotification;
            this.notifyAborted = notifyAborted;
            this.notifyFailure = notifyFailure;
            this.notifyNotBuilt = notifyNotBuilt;
            this.notifySuccess = notifySuccess;
            this.notifyUnstable = notifyUnstable;
            this.notifyBackToNormal = notifyBackToNormal;
        }

        @Exported
        public String getRoom() {
            return room;
        }

        @Exported
        public boolean getStartNotification() {
            return startNotification;
        }

        @Exported
        public boolean getNotifySuccess() {
            return notifySuccess;
        }

        @Exported
        public boolean getNotifyAborted() {
            return notifyAborted;
        }

        @Exported
        public boolean getNotifyFailure() {
            return notifyFailure;
        }

        @Exported
        public boolean getNotifyNotBuilt() {
            return notifyNotBuilt;
        }

        @Exported
        public boolean getNotifyUnstable() {
            return notifyUnstable;
        }

        @Exported
        public boolean getNotifyBackToNormal() {
            return notifyBackToNormal;
        }

        @Extension
        public static final class DescriptorImpl extends JobPropertyDescriptor {
            public String getDisplayName() {
                return "HipChat Notifications";
            }

            @Override
            public boolean isApplicable(Class<? extends Job> jobType) {
                return true;
            }

            @Override
            public HipChatJobProperty newInstance(StaplerRequest sr, JSONObject formData) throws hudson.model.Descriptor.FormException {
                return new HipChatJobProperty(sr.getParameter("hipChatProjectRoom"),
                        sr.getParameter("hipChatStartNotification") != null,
                        sr.getParameter("hipChatNotifyAborted") != null,
                        sr.getParameter("hipChatNotifyFailure") != null,
                        sr.getParameter("hipChatNotifyNotBuilt") != null,
                        sr.getParameter("hipChatNotifySuccess") != null,
                        sr.getParameter("hipChatNotifyUnstable") != null,
                        sr.getParameter("hipChatNotifyBackToNormal") != null);
            }
        }
    }
}
