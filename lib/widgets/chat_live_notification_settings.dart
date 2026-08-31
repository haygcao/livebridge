import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ChatLiveNotificationSettings extends StatefulWidget {
  const ChatLiveNotificationSettings({Key? key}) : super(key: key);

  @override
  State<ChatLiveNotificationSettings> createState() => _ChatLiveNotificationSettingsState();
}

class _ChatLiveNotificationSettingsState extends State<ChatLiveNotificationSettings> {
  bool _perChatEnabled = false;
  String? _packageName;
  String? _chatName;
  final _chatController = TextEditingController();
  final _pkgController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadPrefs();
  }

  Future<void> _loadPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _perChatEnabled = prefs.getBool('live_notifications_per_chat_enabled') ?? false;
      _packageName = prefs.getString('live_notifications_package') ?? '';
      _chatName = prefs.getString('live_notifications_chat_name') ?? '';
      _pkgController.text = _packageName ?? '';
      _chatController.text = _chatName ?? '';
    });
  }

  Future<void> _savePrefs() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('live_notifications_per_chat_enabled', _perChatEnabled);
    await prefs.setString('live_notifications_package', _packageName ?? '');
    await prefs.setString('live_notifications_chat_name', _chatName ?? '');
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Chat live-notification settings saved')));
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SwitchListTile(
          title: const Text('Enable chat-specific Live Notifications'),
          value: _perChatEnabled,
          onChanged: (v) {
            setState(() => _perChatEnabled = v);
          },
        ),
        TextField(
          controller: _pkgController,
          decoration: const InputDecoration(
            labelText: 'App package (e.g. com.whatsapp)',
            helperText: 'Optional: leave blank to match any package',
          ),
          onChanged: (v) => _packageName = v.trim(),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _chatController,
          decoration: const InputDecoration(
            labelText: 'Exact chat/conversation title',
            helperText: 'Exact title as shown in the notification (e.g. "Alice", "Family group")',
          ),
          onChanged: (v) => _chatName = v.trim(),
        ),
        const SizedBox(height: 12),
        ElevatedButton(
          onPressed: _savePrefs,
          child: const Text('Save chat-specific settings'),
        ),
      ],
    );
  }

  @override
  void dispose() {
    _chatController.dispose();
    _pkgController.dispose();
    super.dispose();
  }
}
