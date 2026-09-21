using System;
using System.Drawing;
using System.IO;
using System.Windows.Forms;
using DSDPlusLauncher.Models;

namespace DSDPlusLauncher.Forms
{
    public class ProfileEditForm : Form
    {
        private readonly TextBox _nameBox = new TextBox { Width = 320 };
        private readonly TextBox _exePathBox = new TextBox { Width = 260 };
        private readonly TextBox _workingDirBox = new TextBox { Width = 260 };
        private readonly TextBox _argumentsBox = new TextBox { Width = 320, Multiline = true, Height = 40 };
        private readonly TextBox _notesBox = new TextBox { Width = 320, Multiline = true, Height = 50 };
        private readonly CheckBox _startMinimizedBox = new CheckBox { Text = "Demarrer la fenetre de DSDPlus reduite", AutoSize = true };
        private readonly CheckBox _autoRestartBox = new CheckBox { Text = "Redemarrer automatiquement en cas d'arret inattendu", AutoSize = true };

        public LauncherProfile Profile { get; }

        public ProfileEditForm(LauncherProfile profile)
        {
            Profile = profile ?? throw new ArgumentNullException(nameof(profile));

            Text = "Profil DSDPlus";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            StartPosition = FormStartPosition.CenterParent;
            MaximizeBox = false;
            MinimizeBox = false;
            ClientSize = new Size(420, 430);

            BuildUi();
            LoadFromProfile();
        }

        private void BuildUi()
        {
            var layout = new TableLayoutPanel
            {
                Dock = DockStyle.Top,
                ColumnCount = 1,
                AutoSize = true,
                Padding = new Padding(12)
            };

            layout.Controls.Add(new Label { Text = "Nom du profil :", AutoSize = true, Margin = new Padding(0, 0, 0, 2) });
            layout.Controls.Add(_nameBox);

            layout.Controls.Add(new Label { Text = "Chemin vers DSDPlus.exe :", AutoSize = true, Margin = new Padding(0, 10, 0, 2) });
            layout.Controls.Add(BuildBrowseRow(_exePathBox, BrowseForExecutable));

            layout.Controls.Add(new Label { Text = "Dossier de travail (facultatif) :", AutoSize = true, Margin = new Padding(0, 10, 0, 2) });
            layout.Controls.Add(BuildBrowseRow(_workingDirBox, BrowseForFolder));

            layout.Controls.Add(new Label { Text = "Arguments de ligne de commande :", AutoSize = true, Margin = new Padding(0, 10, 0, 2) });
            layout.Controls.Add(BuildArgumentsRow());

            layout.Controls.Add(_startMinimizedBox);
            layout.Controls.Add(_autoRestartBox);

            layout.Controls.Add(new Label { Text = "Notes :", AutoSize = true, Margin = new Padding(0, 10, 0, 2) });
            layout.Controls.Add(_notesBox);

            Controls.Add(layout);

            var okButton = new Button { Text = "OK", Width = 90 };
            var cancelButton = new Button { Text = "Annuler", DialogResult = DialogResult.Cancel, Width = 90 };
            okButton.Location = new Point(ClientSize.Width - 200, ClientSize.Height - 40);
            cancelButton.Location = new Point(ClientSize.Width - 100, ClientSize.Height - 40);
            okButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            cancelButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            okButton.Click += OkButton_Click;
            Controls.Add(okButton);
            Controls.Add(cancelButton);
            AcceptButton = okButton;
            CancelButton = cancelButton;
        }

        private Control BuildBrowseRow(TextBox box, EventHandler onBrowse)
        {
            var panel = new FlowLayoutPanel { AutoSize = true, WrapContents = false };
            var browseButton = new Button { Text = "Parcourir...", Width = 90 };
            browseButton.Click += onBrowse;
            panel.Controls.Add(box);
            panel.Controls.Add(browseButton);
            return panel;
        }

        private Control BuildArgumentsRow()
        {
            var panel = new FlowLayoutPanel { AutoSize = true, WrapContents = false };
            var assistantButton = new Button { Text = "Assistant...", Width = 90, Height = _argumentsBox.Height };
            assistantButton.Click += (_, _) =>
            {
                using var assistant = new ArgumentAssistantForm();
                if (assistant.ShowDialog(this) == DialogResult.OK)
                {
                    _argumentsBox.Text = assistant.ResultArguments;
                }
            };
            panel.Controls.Add(_argumentsBox);
            panel.Controls.Add(assistantButton);
            return panel;
        }

        private void BrowseForExecutable(object sender, EventArgs e)
        {
            using var dialog = new OpenFileDialog
            {
                Title = "Localisez DSDPlus.exe",
                Filter = "Executables (*.exe)|*.exe|Tous les fichiers (*.*)|*.*"
            };

            if (dialog.ShowDialog(this) == DialogResult.OK)
            {
                _exePathBox.Text = dialog.FileName;
                if (string.IsNullOrWhiteSpace(_workingDirBox.Text))
                {
                    _workingDirBox.Text = Path.GetDirectoryName(dialog.FileName) ?? string.Empty;
                }
            }
        }

        private void BrowseForFolder(object sender, EventArgs e)
        {
            using var dialog = new FolderBrowserDialog { Description = "Choisissez le dossier de travail" };
            if (dialog.ShowDialog(this) == DialogResult.OK)
            {
                _workingDirBox.Text = dialog.SelectedPath;
            }
        }

        private void LoadFromProfile()
        {
            _nameBox.Text = Profile.Name;
            _exePathBox.Text = Profile.ExecutablePath;
            _workingDirBox.Text = Profile.WorkingDirectory;
            _argumentsBox.Text = Profile.Arguments;
            _notesBox.Text = Profile.Notes;
            _startMinimizedBox.Checked = Profile.StartMinimized;
            _autoRestartBox.Checked = Profile.AutoRestartOnCrash;
        }

        private void OkButton_Click(object sender, EventArgs e)
        {
            if (string.IsNullOrWhiteSpace(_nameBox.Text))
            {
                MessageBox.Show(this, "Merci de donner un nom a ce profil.", "DSDPlus Launcher",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            if (string.IsNullOrWhiteSpace(_exePathBox.Text) || !File.Exists(_exePathBox.Text))
            {
                MessageBox.Show(this, "Le chemin vers DSDPlus.exe est introuvable.", "DSDPlus Launcher",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            Profile.Name = _nameBox.Text.Trim();
            Profile.ExecutablePath = _exePathBox.Text.Trim();
            Profile.WorkingDirectory = _workingDirBox.Text.Trim();
            Profile.Arguments = _argumentsBox.Text.Trim();
            Profile.Notes = _notesBox.Text.Trim();
            Profile.StartMinimized = _startMinimizedBox.Checked;
            Profile.AutoRestartOnCrash = _autoRestartBox.Checked;

            DialogResult = DialogResult.OK;
            Close();
        }
    }
}
