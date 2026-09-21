using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Windows.Forms;
using DSDPlusLauncher.Forms;
using DSDPlusLauncher.Models;
using DSDPlusLauncher.Services;

namespace DSDPlusLauncher
{
    public class MainForm : Form
    {
        private const string DsdPlusWebsite = "https://www.dsdplus.com";

        private readonly ProfileStore _store = new ProfileStore();
        private readonly ProcessRunner _runner = new ProcessRunner();
        private readonly System.Collections.Generic.List<LauncherProfile> _profiles;

        private ListBox _profileListBox;
        private Label _statusLabel;
        private Label _summaryExeLabel;
        private Label _summaryDirLabel;
        private Label _summaryArgsLabel;
        private Button _startButton;
        private Button _stopButton;
        private TextBox _logBox;
        private NotifyIcon _notifyIcon;
        private StatusStrip _statusStrip;
        private ToolStripStatusLabel _statusStripLabel;

        private int _restartAttempts;

        public MainForm()
        {
            Text = "DSDPlus Launcher";
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(760, 540);
            MinimumSize = new Size(700, 480);

            _profiles = _store.Load();

            BuildMenu();
            BuildLayout();
            BuildTrayIcon();

            _runner.LogReceived += Runner_LogReceived;
            _runner.ProcessExited += Runner_ProcessExited;

            RefreshProfileList();
            FormClosing += MainForm_FormClosing;
        }

        private void BuildMenu()
        {
            var menu = new MenuStrip();

            var fileMenu = new ToolStripMenuItem("Fichier");
            var newProfileItem = new ToolStripMenuItem("Nouveau profil...", null, (_, _) => CreateProfile());
            var exitItem = new ToolStripMenuItem("Quitter", null, (_, _) => Close());
            fileMenu.DropDownItems.Add(newProfileItem);
            fileMenu.DropDownItems.Add(new ToolStripSeparator());
            fileMenu.DropDownItems.Add(exitItem);

            var profileMenu = new ToolStripMenuItem("Profil");
            profileMenu.DropDownItems.Add(new ToolStripMenuItem("Modifier...", null, (_, _) => EditSelectedProfile()));
            profileMenu.DropDownItems.Add(new ToolStripMenuItem("Dupliquer", null, (_, _) => DuplicateSelectedProfile()));
            profileMenu.DropDownItems.Add(new ToolStripMenuItem("Supprimer", null, (_, _) => DeleteSelectedProfile()));
            profileMenu.DropDownItems.Add(new ToolStripSeparator());
            profileMenu.DropDownItems.Add(new ToolStripMenuItem("Ouvrir le dossier du profil", null, (_, _) => OpenSelectedProfileFolder()));

            var toolsMenu = new ToolStripMenuItem("Outils");
            toolsMenu.DropDownItems.Add(new ToolStripMenuItem("Ouvrir le dossier de configuration", null, (_, _) =>
                OpenFolder(_store.ConfigDirectory)));

            var helpMenu = new ToolStripMenuItem("Aide");
            helpMenu.DropDownItems.Add(new ToolStripMenuItem("Site officiel DSDPlus", null, (_, _) => OpenUrl(DsdPlusWebsite)));
            helpMenu.DropDownItems.Add(new ToolStripMenuItem("A propos", null, (_, _) => ShowAbout()));

            menu.Items.Add(fileMenu);
            menu.Items.Add(profileMenu);
            menu.Items.Add(toolsMenu);
            menu.Items.Add(helpMenu);

            MainMenuStrip = menu;
            Controls.Add(menu);
        }

        private void BuildLayout()
        {
            var split = new SplitContainer
            {
                Dock = DockStyle.Fill,
                Orientation = Orientation.Vertical,
                SplitterDistance = 220
            };

            // --- Panneau gauche : liste des profils ---
            var leftPanel = new Panel { Dock = DockStyle.Fill };

            _profileListBox = new ListBox { Dock = DockStyle.Fill };
            _profileListBox.SelectedIndexChanged += (_, _) => RefreshSummary();
            _profileListBox.DoubleClick += (_, _) => EditSelectedProfile();

            var profileButtons = new FlowLayoutPanel
            {
                Dock = DockStyle.Bottom,
                FlowDirection = FlowDirection.LeftToRight,
                AutoSize = true,
                Padding = new Padding(4)
            };
            var newButton = new Button { Text = "Nouveau", Width = 70 };
            newButton.Click += (_, _) => CreateProfile();
            var editButton = new Button { Text = "Modifier", Width = 70 };
            editButton.Click += (_, _) => EditSelectedProfile();
            var dupButton = new Button { Text = "Dupliquer", Width = 70 };
            dupButton.Click += (_, _) => DuplicateSelectedProfile();
            var delButton = new Button { Text = "Supprimer", Width = 70 };
            delButton.Click += (_, _) => DeleteSelectedProfile();
            profileButtons.Controls.Add(newButton);
            profileButtons.Controls.Add(editButton);
            profileButtons.Controls.Add(dupButton);
            profileButtons.Controls.Add(delButton);

            leftPanel.Controls.Add(_profileListBox);
            leftPanel.Controls.Add(profileButtons);
            leftPanel.Controls.Add(new Label { Text = "Profils", Dock = DockStyle.Top, Padding = new Padding(4), Font = new Font(Font, FontStyle.Bold) });

            split.Panel1.Controls.Add(leftPanel);

            // --- Panneau droit : details + journal ---
            var rightSplit = new SplitContainer
            {
                Dock = DockStyle.Fill,
                Orientation = Orientation.Horizontal,
                SplitterDistance = 170
            };

            var detailsGroup = new GroupBox { Text = "Profil selectionne", Dock = DockStyle.Fill };
            var detailsLayout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, Padding = new Padding(8) };

            _summaryExeLabel = new Label { AutoSize = true, Text = "Executable : -" };
            _summaryDirLabel = new Label { AutoSize = true, Text = "Dossier de travail : -" };
            _summaryArgsLabel = new Label { AutoSize = true, Text = "Arguments : -" };
            _statusLabel = new Label { AutoSize = true, Text = "Etat : arrete", ForeColor = Color.DarkRed, Font = new Font(Font, FontStyle.Bold) };

            var actionButtons = new FlowLayoutPanel { AutoSize = true, Margin = new Padding(0, 8, 0, 0) };
            _startButton = new Button { Text = "Demarrer", Width = 100, Height = 32 };
            _startButton.Click += (_, _) => StartSelectedProfile();
            _stopButton = new Button { Text = "Arreter", Width = 100, Height = 32, Enabled = false };
            _stopButton.Click += (_, _) => StopRunningProfile();
            actionButtons.Controls.Add(_startButton);
            actionButtons.Controls.Add(_stopButton);

            detailsLayout.Controls.Add(_summaryExeLabel);
            detailsLayout.Controls.Add(_summaryDirLabel);
            detailsLayout.Controls.Add(_summaryArgsLabel);
            detailsLayout.Controls.Add(_statusLabel);
            detailsLayout.Controls.Add(actionButtons);
            detailsGroup.Controls.Add(detailsLayout);

            var logGroup = new GroupBox { Text = "Journal", Dock = DockStyle.Fill };
            _logBox = new TextBox
            {
                Dock = DockStyle.Fill,
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                Font = new Font(FontFamily.GenericMonospace, 8.5F)
            };
            var logButtons = new FlowLayoutPanel { Dock = DockStyle.Bottom, AutoSize = true };
            var clearLogButton = new Button { Text = "Effacer", Width = 80 };
            clearLogButton.Click += (_, _) => _logBox.Clear();
            logButtons.Controls.Add(clearLogButton);
            logGroup.Controls.Add(_logBox);
            logGroup.Controls.Add(logButtons);

            rightSplit.Panel1.Controls.Add(detailsGroup);
            rightSplit.Panel2.Controls.Add(logGroup);

            split.Panel2.Controls.Add(rightSplit);

            Controls.Add(split);
            split.BringToFront();

            _statusStrip = new StatusStrip();
            _statusStripLabel = new ToolStripStatusLabel("Pret.");
            _statusStrip.Items.Add(_statusStripLabel);
            Controls.Add(_statusStrip);
        }

        private void BuildTrayIcon()
        {
            var menu = new ContextMenuStrip();
            menu.Items.Add("Afficher", null, (_, _) => RestoreFromTray());
            menu.Items.Add("Demarrer", null, (_, _) => StartSelectedProfile());
            menu.Items.Add("Arreter", null, (_, _) => StopRunningProfile());
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("Quitter", null, (_, _) => Close());

            _notifyIcon = new NotifyIcon
            {
                Icon = SystemIcons.Application,
                Text = "DSDPlus Launcher",
                ContextMenuStrip = menu,
                Visible = true
            };
            _notifyIcon.DoubleClick += (_, _) => RestoreFromTray();
        }

        private void RestoreFromTray()
        {
            Show();
            WindowState = FormWindowState.Normal;
            Activate();
        }

        private LauncherProfile SelectedProfile =>
            _profileListBox.SelectedItem as LauncherProfile;

        private void RefreshProfileList()
        {
            var selectedId = SelectedProfile?.Id;
            _profileListBox.Items.Clear();
            foreach (var profile in _profiles)
            {
                _profileListBox.Items.Add(profile);
            }

            if (selectedId != null)
            {
                var match = _profiles.FirstOrDefault(p => p.Id == selectedId);
                if (match != null)
                {
                    _profileListBox.SelectedItem = match;
                }
            }
            else if (_profileListBox.Items.Count > 0)
            {
                _profileListBox.SelectedIndex = 0;
            }

            RefreshSummary();
        }

        private void RefreshSummary()
        {
            var profile = SelectedProfile;
            if (profile == null)
            {
                _summaryExeLabel.Text = "Executable : -";
                _summaryDirLabel.Text = "Dossier de travail : -";
                _summaryArgsLabel.Text = "Arguments : -";
                _startButton.Enabled = false;
                return;
            }

            _summaryExeLabel.Text = "Executable : " + profile.ExecutablePath;
            _summaryDirLabel.Text = "Dossier de travail : " +
                (string.IsNullOrWhiteSpace(profile.WorkingDirectory) ? "(dossier de l'executable)" : profile.WorkingDirectory);
            _summaryArgsLabel.Text = "Arguments : " + (string.IsNullOrWhiteSpace(profile.Arguments) ? "(aucun)" : profile.Arguments);
            _startButton.Enabled = !_runner.IsRunning;
        }

        private void CreateProfile()
        {
            var profile = new LauncherProfile();
            using var form = new ProfileEditForm(profile);
            if (form.ShowDialog(this) == DialogResult.OK)
            {
                _profiles.Add(profile);
                SaveProfiles();
                RefreshProfileList();
                _profileListBox.SelectedItem = profile;
            }
        }

        private void EditSelectedProfile()
        {
            var profile = SelectedProfile;
            if (profile == null) return;

            using var form = new ProfileEditForm(profile);
            if (form.ShowDialog(this) == DialogResult.OK)
            {
                SaveProfiles();
                RefreshProfileList();
            }
        }

        private void DuplicateSelectedProfile()
        {
            var profile = SelectedProfile;
            if (profile == null) return;

            var copy = profile.Clone();
            _profiles.Add(copy);
            SaveProfiles();
            RefreshProfileList();
            _profileListBox.SelectedItem = copy;
        }

        private void DeleteSelectedProfile()
        {
            var profile = SelectedProfile;
            if (profile == null) return;

            if (_runner.IsRunning)
            {
                MessageBox.Show(this, "Arretez d'abord DSDPlus avant de supprimer ce profil.", "DSDPlus Launcher",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            var confirm = MessageBox.Show(this, $"Supprimer le profil \"{profile.Name}\" ?", "DSDPlus Launcher",
                MessageBoxButtons.YesNo, MessageBoxIcon.Question);
            if (confirm != DialogResult.Yes) return;

            _profiles.Remove(profile);
            SaveProfiles();
            RefreshProfileList();
        }

        private void OpenSelectedProfileFolder()
        {
            var profile = SelectedProfile;
            if (profile == null || string.IsNullOrWhiteSpace(profile.ExecutablePath)) return;

            var dir = Path.GetDirectoryName(profile.ExecutablePath);
            OpenFolder(dir);
        }

        private void SaveProfiles()
        {
            _store.Save(_profiles);
        }

        private void StartSelectedProfile()
        {
            var profile = SelectedProfile;
            if (profile == null)
            {
                MessageBox.Show(this, "Selectionnez ou creez d'abord un profil.", "DSDPlus Launcher",
                    MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            if (_runner.IsRunning)
            {
                MessageBox.Show(this, "DSDPlus est deja en cours d'execution.", "DSDPlus Launcher",
                    MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            try
            {
                _restartAttempts = 0;
                _runner.Start(profile);
                AppendLog($"--- Demarrage du profil \"{profile.Name}\" ---");
                UpdateRunningState(true);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "Impossible de demarrer DSDPlus :\n" + ex.Message, "DSDPlus Launcher",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void StopRunningProfile()
        {
            if (!_runner.IsRunning) return;
            _runner.Stop();
            AppendLog("--- Arret demande par l'utilisateur ---");
        }

        private void Runner_LogReceived(object sender, string line)
        {
            AppendLog(line);
        }

        private void Runner_ProcessExited(object sender, int exitCode)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action(() => Runner_ProcessExited(sender, exitCode)));
                return;
            }

            AppendLog($"--- DSDPlus s'est arrete (code {exitCode}) ---");
            UpdateRunningState(false);

            var profile = SelectedProfile;
            if (!_runner.WasManualStop && profile != null && profile.AutoRestartOnCrash && _restartAttempts < 5)
            {
                _restartAttempts++;
                AppendLog($"Redemarrage automatique ({_restartAttempts}/5)...");
                var timer = new System.Windows.Forms.Timer { Interval = 3000 };
                timer.Tick += (_, _) =>
                {
                    timer.Stop();
                    timer.Dispose();
                    try
                    {
                        _runner.Start(profile);
                        UpdateRunningState(true);
                    }
                    catch (Exception ex)
                    {
                        AppendLog("Echec du redemarrage automatique : " + ex.Message);
                    }
                };
                timer.Start();
            }
            else
            {
                _restartAttempts = 0;
            }
        }

        private void UpdateRunningState(bool running)
        {
            _statusLabel.Text = running ? "Etat : en cours d'execution" : "Etat : arrete";
            _statusLabel.ForeColor = running ? Color.DarkGreen : Color.DarkRed;
            _startButton.Enabled = !running;
            _stopButton.Enabled = running;
            _statusStripLabel.Text = running ? "DSDPlus est en cours d'execution." : "Pret.";
        }

        private void AppendLog(string line)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action(() => AppendLog(line)));
                return;
            }

            _logBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {line}{Environment.NewLine}");
        }

        private static void OpenUrl(string url)
        {
            try
            {
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            }
            catch
            {
                // Ignore : impossible d'ouvrir le navigateur par defaut.
            }
        }

        private static void OpenFolder(string path)
        {
            if (string.IsNullOrWhiteSpace(path) || !Directory.Exists(path)) return;
            try
            {
                Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
            }
            catch
            {
                // Ignore : impossible d'ouvrir l'explorateur.
            }
        }

        private void ShowAbout()
        {
            MessageBox.Show(this,
                "DSDPlus Launcher\n\n" +
                "Interface graphique simple pour gerer plusieurs profils DSDPlus, " +
                "choisir vos peripheriques audio et demarrer/arreter DSDPlus en un clic.\n\n" +
                "DSDPlus n'est pas fourni avec cette application : telechargez-le depuis " +
                DsdPlusWebsite + ".",
                "A propos", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        private void MainForm_FormClosing(object sender, FormClosingEventArgs e)
        {
            if (_runner.IsRunning)
            {
                var result = MessageBox.Show(this,
                    "DSDPlus est toujours en cours d'execution. Voulez-vous l'arreter avant de quitter ?",
                    "DSDPlus Launcher", MessageBoxButtons.YesNoCancel, MessageBoxIcon.Question);

                if (result == DialogResult.Cancel)
                {
                    e.Cancel = true;
                    return;
                }

                if (result == DialogResult.Yes)
                {
                    _runner.Stop();
                }
            }

            _notifyIcon.Visible = false;
            _notifyIcon.Dispose();
            _runner.Dispose();
        }
    }
}
