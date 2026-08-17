Name:           dragonpal
Version:        1.0.0
Release:        1%{?dist}
Summary:        A little AI dragon that lives on your screen
License:        Proprietary
BuildArch:      noarch
Requires:       python3
Requires:       python3-tkinter
Source0:        dragonpal-%{version}.tar.gz

%description
DragonPal is a floating desktop AI companion. It roams your screen, talks
out loud, remembers you, reacts to text you copy, and can look at your
screen with a vision model.

%prep
%setup -q -n dragonpal-%{version}

%install
mkdir -p %{buildroot}/opt/dragonpal
cp -r dragonpal %{buildroot}/opt/dragonpal/
install -Dm755 dragonpal-launcher %{buildroot}/usr/bin/dragonpal
install -Dm644 DragonPal.desktop %{buildroot}/usr/share/applications/DragonPal.desktop
install -Dm644 dragonpal.png %{buildroot}/usr/share/icons/hicolor/256x256/apps/dragonpal.png

%files
/opt/dragonpal
/usr/bin/dragonpal
/usr/share/applications/DragonPal.desktop
/usr/share/icons/hicolor/256x256/apps/dragonpal.png

%changelog
* Mon Aug 17 2026 Haxnstuff <jlukasgammon@gmail.com> - 1.0.0-1
- Initial Linux package
