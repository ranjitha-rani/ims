import streamlit as st
from admin import admin
from customer import customer


def main():
    st.title("INSURANCE MANAGEMENT SYSTEM")
    st.markdown('***')

    menu = ["Home", "Admin", "Customer"]
    choice = st.sidebar.selectbox("Are you Agent or customer??", menu)

    if choice == 'home':
        st.sidebar.subheader("Home")

    elif choice == 'Admin':
        # st.sidebar.subheader("Welcome!!")
        admin()

    elif choice == 'Customer':
        # st.sidebar.subheader("Welcome!!")
        customer()


if __name__ == '__main__':
    main()
