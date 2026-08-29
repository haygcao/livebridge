import re
import os

def clean_rules_file():
    path = 'lib/screens/redesign/rules_per_app_behavior_screen.dart'
    if not os.path.exists(path): return
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    pattern = r'final\s+FilePickerResult\?\s+result\s*=\s*await\s+FilePicker.*?if\s*\(result\s*==\s*null'
    clean_block = """final FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const <String>['json'],
        withData: true,
      );
      if (result == null"""
    
    content = re.sub(pattern, clean_block, content, flags=re.DOTALL)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

def clean_backup_file():
    path = 'lib/screens/redesign/settings_backup_restore_screen.dart'
    if not os.path.exists(path): return
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    content = content.replace("Import 'dart:async';", "import 'dart:async';")
    
    pattern = r'final\s+FilePickerResult\?\s+result\s*=\s*await\s+FilePicker.*?if\s*\(result\s*==\s*null'
    clean_block = """final FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const <String>['lbst'],
        withData: true,
      );
      if (result == null"""
    
    content = re.sub(pattern, clean_block, content, flags=re.DOTALL)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == "__main__":
    clean_rules_file()
    clean_backup_file()
    print("Files patched successfully!")

