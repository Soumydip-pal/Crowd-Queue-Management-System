import { createContext, useContext, useEffect, useState } from "react";

export const ThemeContext = createContext();

function getInitialTheme() {
  try {
    const saved = window.localStorage.getItem("crowd_theme");
    if (saved === "dark" || saved === "light") {
      return saved === "dark";
    }
    return window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
  } catch (err) {
    return false;
  }
}

export function ThemeProvider({ children }) {
  const [dark, setDark] = useState(getInitialTheme);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", dark ? "dark" : "light");
    try {
      window.localStorage.setItem("crowd_theme", dark ? "dark" : "light");
    } catch (err) {
      // ignore storage errors (private browsing, etc.)
    }
  }, [dark]);

  return (
    <ThemeContext.Provider value={{ dark, setDark, toggleTheme: () => setDark((d) => !d) }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}
