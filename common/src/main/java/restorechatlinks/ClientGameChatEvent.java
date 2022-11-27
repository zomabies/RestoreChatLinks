package restorechatlinks;

import net.minecraft.text.Text;

public class ClientGameChatEvent {
    private Text message;
    private boolean cancelled;

    public ClientGameChatEvent(Text message) {
        this.message = message;
    }

    public Text getMessage() {
        return message;
    }

    public void setMessage(Text message) {
        this.message = message;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
