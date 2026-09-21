using System;

namespace DSDPlusLauncher.Models
{
    /// <summary>
    /// Represente un profil de lancement DSDPlus (un scanner / une configuration audio donnee).
    /// </summary>
    public class LauncherProfile
    {
        public string Id { get; set; } = Guid.NewGuid().ToString("N");

        public string Name { get; set; } = "Nouveau profil";

        public string ExecutablePath { get; set; } = string.Empty;

        public string WorkingDirectory { get; set; } = string.Empty;

        public string Arguments { get; set; } = string.Empty;

        public bool StartMinimized { get; set; }

        public bool AutoRestartOnCrash { get; set; }

        public string Notes { get; set; } = string.Empty;

        public LauncherProfile Clone()
        {
            return new LauncherProfile
            {
                Id = Guid.NewGuid().ToString("N"),
                Name = Name + " (copie)",
                ExecutablePath = ExecutablePath,
                WorkingDirectory = WorkingDirectory,
                Arguments = Arguments,
                StartMinimized = StartMinimized,
                AutoRestartOnCrash = AutoRestartOnCrash,
                Notes = Notes
            };
        }

        public override string ToString() => Name;
    }
}
