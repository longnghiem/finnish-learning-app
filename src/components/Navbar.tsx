import {useAuth} from "../auth/useAuth.ts";
import {Link, useNavigate} from "react-router-dom";

/**
 * Persistent navigation bar displayed on every page.
 */
export function Navbar() {
    const { isLoggedIn, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate("/");
    };

    return (
        <nav className="flex items-center justify-between bg-white px-6 py-4 shadow-sm">
            <Link to="/" className="text-xl font-bold text-gray-800 hover:text-gray-600">
                Finnish Learning App
            </Link>
            <div>
                {isLoggedIn ? (
                    <button
                        onClick={handleLogout}
                        className="rounded-md bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600"
                    >
                        Logout
                    </button>
                ) : (
                    <Link
                        to="/login"
                        className="rounded-md bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
                    >
                        Login
                    </Link>
                )}
            </div>
        </nav>
    );
}