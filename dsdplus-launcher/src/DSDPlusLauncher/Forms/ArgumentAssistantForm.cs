using System;
using System.Drawing;
using System.Windows.Forms;
using DSDPlusLauncher.Services;

namespace DSDPlusLauncher.Forms
{
    /// <summary>
    /// Petit assistant qui aide a construire la ligne d'arguments a partir des
    /// peripheriques audio detectes. Les modeles proposes sont indicatifs :
    /// la syntaxe exacte attendue par DSDPlus depend de la version installee
    /// (voir le fichier DSDPlus.txt fourni avec le logiciel).
    /// </summary>
    public class ArgumentAssistantForm : Form
    {
        private readonly ComboBox _inputCombo = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 320 };
        private readonly ComboBox _outputCombo = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 320 };
        private readonly RadioButton _positionalRadio = new RadioButton { Text = "Parametres positionnels (ex. : 1 3)", Checked = true, AutoSize = true };
        private readonly RadioButton _switchesRadio = new RadioButton { Text = "Commutateurs -i / -o (ex. : -i1 -o3)", AutoSize = true };
        private readonly TextBox _extraArgsBox = new TextBox { Width = 320 };
        private readonly Label _previewLabel = new Label { AutoSize = true, Font = new Font(FontFamily.GenericMonospace, 9F) };

        public string ResultArguments { get; private set; } = string.Empty;

        public ArgumentAssistantForm()
        {
            Text = "Assistant de parametres";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            StartPosition = FormStartPosition.CenterParent;
            MaximizeBox = false;
            MinimizeBox = false;
            ClientSize = new Size(420, 460);

            BuildUi();
            LoadDevices();
            UpdatePreview();
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

            layout.Controls.Add(new Label { Text = "Peripherique d'entree (audio a decoder) :", AutoSize = true, Margin = new Padding(0, 0, 0, 2) });
            layout.Controls.Add(_inputCombo);
            layout.Controls.Add(new Label { Text = "Peripherique de sortie (audio decode) :", AutoSize = true, Margin = new Padding(0, 10, 0, 2) });
            layout.Controls.Add(_outputCombo);

            layout.Controls.Add(new Label { Text = "Format des parametres :", AutoSize = true, Margin = new Padding(0, 12, 0, 2) });
            layout.Controls.Add(_positionalRadio);
            layout.Controls.Add(_switchesRadio);

            layout.Controls.Add(new Label { Text = "Arguments supplementaires (facultatif) :", AutoSize = true, Margin = new Padding(0, 10, 0, 2) });
            layout.Controls.Add(_extraArgsBox);

            layout.Controls.Add(new Label
            {
                Text = "Apercu :",
                AutoSize = true,
                Margin = new Padding(0, 12, 0, 2)
            });
            layout.Controls.Add(_previewLabel);

            var warningLabel = new Label
            {
                Text = "Ces modeles sont indicatifs. Verifiez la syntaxe exacte dans le fichier " +
                       "DSDPlus.txt fourni avec votre version de DSDPlus (elle peut varier selon " +
                       "les versions : standard, Fastlane, etc.).",
                AutoSize = false,
                Width = 370,
                Height = 55,
                ForeColor = Color.DimGray,
                Font = new Font(Control.DefaultFont.FontFamily, 8F),
                Margin = new Padding(0, 12, 0, 0)
            };
            layout.Controls.Add(warningLabel);

            Controls.Add(layout);

            var okButton = new Button { Text = "Utiliser", DialogResult = DialogResult.OK, Width = 90 };
            var cancelButton = new Button { Text = "Annuler", DialogResult = DialogResult.Cancel, Width = 90 };
            okButton.Location = new Point(ClientSize.Width - 200, ClientSize.Height - 40);
            cancelButton.Location = new Point(ClientSize.Width - 100, ClientSize.Height - 40);
            okButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            cancelButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            okButton.Click += (_, _) => { ResultArguments = BuildArguments(); };
            Controls.Add(okButton);
            Controls.Add(cancelButton);
            AcceptButton = okButton;
            CancelButton = cancelButton;

            _positionalRadio.CheckedChanged += (_, _) => UpdatePreview();
            _switchesRadio.CheckedChanged += (_, _) => UpdatePreview();
            _extraArgsBox.TextChanged += (_, _) => UpdatePreview();
            _inputCombo.SelectedIndexChanged += (_, _) => UpdatePreview();
            _outputCombo.SelectedIndexChanged += (_, _) => UpdatePreview();
        }

        private void LoadDevices()
        {
            var inputs = AudioDeviceService.GetInputDevices();
            var outputs = AudioDeviceService.GetOutputDevices();

            if (inputs.Count == 0)
            {
                _inputCombo.Items.Add("Aucun peripherique detecte");
            }
            else
            {
                foreach (var device in inputs)
                {
                    _inputCombo.Items.Add(device);
                }
            }

            if (outputs.Count == 0)
            {
                _outputCombo.Items.Add("Aucun peripherique detecte");
            }
            else
            {
                foreach (var device in outputs)
                {
                    _outputCombo.Items.Add(device);
                }
            }

            if (_inputCombo.Items.Count > 0) _inputCombo.SelectedIndex = 0;
            if (_outputCombo.Items.Count > 0) _outputCombo.SelectedIndex = 0;
        }

        private string BuildArguments()
        {
            var inputIndex = (_inputCombo.SelectedItem as AudioDeviceInfo)?.Index;
            var outputIndex = (_outputCombo.SelectedItem as AudioDeviceInfo)?.Index;
            var extra = (_extraArgsBox.Text ?? string.Empty).Trim();

            string devicePart = string.Empty;
            if (inputIndex.HasValue && outputIndex.HasValue)
            {
                devicePart = _positionalRadio.Checked
                    ? $"{inputIndex.Value} {outputIndex.Value}"
                    : $"-i{inputIndex.Value} -o{outputIndex.Value}";
            }

            return string.IsNullOrEmpty(extra) ? devicePart : $"{devicePart} {extra}".Trim();
        }

        private void UpdatePreview()
        {
            _previewLabel.Text = "DSDPlus.exe " + BuildArguments();
        }
    }
}
