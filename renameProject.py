import shutil
from pathlib import Path


def renameFile(old_name, new_name):
    old_path = Path(old_name)
    new_path = Path(new_name)

    if not old_path.exists():
        raise FileNotFoundError(f"The file '{old_name}' does not exist.")

    if new_path.exists():
        raise FileExistsError(f"The file '{new_name}' already exists.")

    shutil.move(old_path, new_path)
    print(f"File renamed from '{old_name}' to '{new_name}'")


def replaceInFile(file_path, old_string, new_string):
    path = Path(file_path)

    if not path.exists():
        raise FileNotFoundError(f"The file '{file_path}' does not exist.")

    with path.open('r', encoding='utf-8') as file:
        content = file.read()

    content = content.replace(old_string, new_string)

    with path.open('w', encoding='utf-8') as file:
        file.write(content)

    print(f"Replaced '{old_string}' with '{new_string}' in '{file_path}'")


def main() -> None:
    old_project_name = "PaperPluginModernTemplate"
    old_package_name = "paperPluginModernTemplate"
    new_project_name = input("Enter the new project name: ").strip()
    new_package_name = new_project_name[0].lower() + new_project_name[1:]
    old_command_name = "PaperTemplateCommand"
    new_command_name = new_project_name + "Command"
    old_command_label = "papertemplate"
    new_command_label = new_project_name.lower()
    # ask if user wants to proceed
    print(f"Old Project Name: {old_project_name}")
    print(f"New Project Name: {new_project_name}")
    print(f"Old Package Name: {old_package_name}")
    print(f"New Package Name: {new_package_name}")
    print(f"New Command Name: {new_command_name}")
    print(f"New Command Label: {new_command_label}")
    proceed = input(f"Are you sure you want to proceed? (y/n): ").strip().lower()
    if proceed != 'y':
        print("Renaming cancelled.")
        return

    # Replace occurrences in files
    words_to_update = [(old_package_name, new_package_name), (old_project_name, new_project_name),
                       (old_command_name, new_command_name),
                       (old_command_label, new_command_label)]
    files_to_update = ["settings.gradle", "build.gradle.kts",
                       f"src/main/java/me/lidan/{old_package_name}/{old_project_name}.java",
                       f"src/main/java/me/lidan/{old_package_name}/commands/{old_command_name}.java",
                       f"src/main/resources/plugin.yml"]
    for file in files_to_update:
        for old_word, new_word in words_to_update:
            replaceInFile(file, old_word, new_word)

    # Rename directories
    old_package_path = Path(f"src/main/java/me/lidan/{old_package_name}")
    new_package_path = Path(f"src/main/java/me/lidan/{new_package_name}")
    if not old_package_path.exists():
        raise FileNotFoundError(f"The directory '{old_package_path}' does not exist.")
    if new_package_path.exists():
        raise FileExistsError(f"The directory '{new_package_path}' already exists.")
    shutil.move(old_package_path, new_package_path)
    print(f"Directory renamed from '{old_package_path}' to '{new_package_path}'")

    # rename java files
    old_main_java_file = f"src/main/java/me/lidan/{new_package_name}/{old_project_name}.java"
    new_main_java_file = f"src/main/java/me/lidan/{new_package_name}/{new_project_name}.java"
    renameFile(old_main_java_file, new_main_java_file)
    old_command_java_file = f"src/main/java/me/lidan/{new_package_name}/commands/{old_command_name}.java"
    new_command_java_file = f"src/main/java/me/lidan/{new_package_name}/commands/{new_command_name}.java"
    renameFile(old_command_java_file, new_command_java_file)


if __name__ == "__main__":
    main()
